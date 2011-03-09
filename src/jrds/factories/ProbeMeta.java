package jrds.factories;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import jrds.factories.xml.JrdsNode;
import jrds.starter.Starter;
import jrds.webapp.DiscoverAgent;

import org.w3c.dom.Element;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ProbeMeta {
    public class EmptyDiscoverAgent extends DiscoverAgent {
        protected EmptyDiscoverAgent(String name) {
            super("Empty");
        }

        @Override
        public void discover(String hostname, Element hostElement,
                Collection<JrdsNode> probdescs, HttpServletRequest request) {
        }

        @Override
        public List<FieldInfo> getFields() {
            return Collections.emptyList();
        }
    };
    
    public class EmptyStarter extends Starter {
    };
    
    Class<? extends DiscoverAgent> discoverAgent() default EmptyDiscoverAgent.class;
    Class<? extends Starter> topStarter() default EmptyStarter.class;

}