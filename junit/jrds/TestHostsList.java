package jrds;

import java.io.IOException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestHostsList {
    static final private Logger logger = Logger.getLogger(TestHostsList.class);
    static final private String[] optionalstabs = {"@", "sumstab", "customgraph" }; 

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @BeforeClass
    static public void configure() throws Exception {
        Tools.configure();
        Tools.setLevel(logger, Level.TRACE, HostsList.class.getName());
    }

    @Test
    public void testDefault() throws IOException {
        PropertiesManager pm = Tools.makePm(testFolder, "tabs=filtertab,customgraph,@,sumstab,servicestab,viewstab,hoststab,tagstab,adminTab");
        HostsList hl = new HostsList(pm);
        Assert.assertEquals("First tab not found", pm.tabsList.get(0), hl.getFirstTab());
        Assert.assertEquals("Missing tabs", pm.tabsList.size() - optionalstabs.length, hl.getTabsId().size());
    }
    
    @Test
    public void testOther() throws IOException {
        PropertiesManager pm = Tools.makePm(testFolder);
        pm.setProperty("tabs", "filtertab");
        pm.update();
        HostsList hl = new HostsList(pm);
        Assert.assertEquals("First tab not found", pm.tabsList.get(0), hl.getFirstTab());
        Assert.assertEquals("Missing tabs", pm.tabsList.size(), hl.getTabsId().size());
    }
}
