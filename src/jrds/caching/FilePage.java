package jrds.caching;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jrds.Util;

import org.apache.log4j.Logger;

public class FilePage {
    static final private Logger logger = Logger.getLogger(FilePage.class);

    native static void prepare_fd(String filename, FileDescriptor fdobj, boolean readOnly) throws IOException ;

    private static final FileChannel DirectFileRead(String path) throws IOException {
        if(path == null)
            throw new NullPointerException();
        FileDescriptor fd = new FileDescriptor();
        prepare_fd(path, fd, true);
        return new FileInputStream(fd).getChannel();
    }

    private static final FileChannel DirectFileWrite(String path) throws IOException {
        if(path == null)
            throw new NullPointerException();
        FileDescriptor fd = new FileDescriptor();
        prepare_fd(path, fd, false);
        return new FileOutputStream(fd).getChannel();
    }

    private final ByteBuffer page;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    final int pageIndex;
    private boolean dirty;
    private int size;
    String filepath;
    long fileOffset;

    /**
     * Used to build an empty page
     */
    public FilePage(ByteBuffer pagecache, int pageIndex) {
        try {
            pagecache.position(pageIndex * PageCache.PAGESIZE);
            this.page = pagecache.slice();
            this.page.limit(0);
            this.pageIndex = pageIndex;
            this.size = 0;
            this.filepath = null;
            logger.trace(Util.delayedFormatString("Sliced at %d, capacity: %d", pagecache.position(), page.capacity()));
        } catch (RuntimeException e) {
            logger.error(Util.delayedFormatString("page creation failed at %d", pageIndex));
            throw e;
        }
    }

    public FilePage(int pageIndex) {
        try {
            this.page = ByteBuffer.allocateDirect(PageCache.PAGESIZE);
            this.page.limit(0);
            this.pageIndex = pageIndex;
            this.size = 0;
            this.filepath = null;
        } catch (RuntimeException e) {
            logger.error(Util.delayedFormatString("page creation failed at %d", pageIndex));
            throw e;
        }
    }

    public void load(File file,
            long offset) throws IOException {
        if(! isEmpty()) {
            throw new IllegalStateException("Loading file page in a none empty cache page");
        }
        //Check if file exists, and create it if needed
        file.createNewFile();
        String canonicalpath = file.getCanonicalPath();
        FileChannel channel = DirectFileRead(canonicalpath);
        lock.writeLock().lock();
        this.filepath = canonicalpath;
        this.fileOffset = PageCache.offsetPage(offset);
        this.page.limit(PageCache.PAGESIZE);
        this.page.position(0);
        this.size = channel.read(page, this.fileOffset);
        lock.writeLock().unlock();
        logger.debug(Util.delayedFormatString("Loaded %d bytes at offset %d from %s in page %d", size, fileOffset, filepath, pageIndex));
        channel.close();
    }

    /**
     * This methode duplicate the byte buffer and set the position for the copy
     * So multiple read can run concurently and not overrite cursor
     * <p>
     * It also acquire the read lock
     * @param position
     * @param limit
     * @return byte buffer whose limits can be safely modified
     */
    private ByteBuffer cloneState(int position, int limit) {
        lock.writeLock().lock();
        ByteBuffer cursor = page.duplicate();
        lock.readLock().lock();
        lock.writeLock().unlock();
        cursor.limit(limit);
        cursor.position(position);
        return cursor;
    }

    /**
     * Sync the content of the page cache
     * @throws IOException
     */
    public void sync() throws IOException {
        FileChannel channel = sync(null);
        if(channel!= null) {
            channel.force(true);
            channel.close();
        }
    }

    /**
     * Sync the content of the page cache reusing an existing channel
     * @param channel the channel to use. If it's null, a direct channle will be openned
     * @return the channel used
     * @throws IOException
     */
    public FileChannel sync(FileChannel channel) throws IOException {
        if(dirty) {
            try {
                logger.debug(Util.delayedFormatString("syncing %d bytes at %d to %s", size, fileOffset, filepath));
                if(channel == null)
                    channel = DirectFileWrite(filepath);
                ByteBuffer cursor = cloneState(0, size);
                channel.write(cursor, fileOffset);
                dirty = false;
                lock.readLock().unlock();
                return channel;
            } catch (IOException e) {
                logger.error(Util.delayedFormatString("sync failed for %s: %s", filepath, e), e);
                throw e;
            } catch (IllegalArgumentException e) {
                logger.error(Util.delayedFormatString("sync failed for %s: %s, writing %d bytes", filepath, e, size), e);
                throw e;
            } catch (RuntimeException e) {
                logger.error(Util.delayedFormatString("sync failed for %s: %s", filepath, e), e);
                throw e;
            }
        }
        return null;
    }

    public void read(long offset, byte[] buffer) throws IOException{
        int pageStartPos = (int) Math.max(0, offset - fileOffset);
        int pageEndPos = (int) Math.min((long)PageCache.PAGESIZE, offset + buffer.length - fileOffset);
        int bytesRead = pageEndPos - pageStartPos;
        int bufferOffset = (int) Math.max(0, fileOffset - offset);
        ByteBuffer cursor = cloneState(pageStartPos, pageEndPos);
        try {
            cursor.get(buffer, bufferOffset, bytesRead);
        } catch (Exception e) {
            logger.error(Util.delayedFormatString("error getting %d bytes at %d, starting at %d, from %d to %d %d %d", buffer.length, offset, fileOffset, pageStartPos, pageEndPos - 1, bufferOffset, bytesRead));
            logger.error(e, e);
        }
        lock.readLock().unlock();
        logger.trace(Util.delayedFormatString("in page %d: read %d bytes at offset %d in file %s: relative to offset %d of file, from %d to %d (%d bytes) in position %d in buffer", pageIndex, buffer.length, offset, filepath, fileOffset, pageStartPos, pageEndPos - 1, bytesRead, bufferOffset));
    }

    public void write(long offset, byte[] buffer) {
        int pageStartPos = (int) Math.max(0, offset - fileOffset);
        int pageEndPos = (int) Math.min((long)PageCache.PAGESIZE, offset + buffer.length - fileOffset);
        int bytesWritten = pageEndPos - pageStartPos;
        int bufferOffset = (int) Math.max(0, fileOffset - offset);
        lock.writeLock().lock();
        try {
            page.limit(pageEndPos);
            page.position(pageStartPos);
            page.put(buffer, bufferOffset, bytesWritten);
            dirty = true;
        } catch (Exception e) {
            logger.error(Util.delayedFormatString("%d %d %d %d", pageStartPos, pageEndPos, bufferOffset, bytesWritten));
            logger.error(e, e);
        }
        lock.writeLock().unlock();
        size = Math.max(size, pageEndPos);
        logger.debug(Util.delayedFormatString("in page %d: write %d bytes at %d from %s, in page %d of file from %d to %d to %d", pageIndex, bytesWritten, offset, filepath, fileOffset / PageCache.PAGESIZE, pageStartPos, pageEndPos, bufferOffset));

    }

    /* (non-Javadoc)
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        sync();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return (Long.toString(fileOffset) + "@" + filepath).hashCode() ;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object arg0) {
        if(arg0.getClass() != this.getClass())
            return false;
        FilePage compared = (FilePage) arg0;
        if(! compared.filepath.equals(this.filepath))
            return false;
        return compared.fileOffset == this.fileOffset;
    }

    public boolean isEmpty() {
        return filepath == null;
    }

    public void free() throws IOException {
        if(filepath != null)
            sync();
        lock.writeLock().lock();
        filepath = null;
        page.limit(0);
        size = 0;
        lock.writeLock().unlock();
    }

    /**
     * @return the dirty
     */
    boolean isDirty() {
        return dirty;
    }
}
