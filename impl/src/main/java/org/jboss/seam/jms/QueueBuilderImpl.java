package org.jboss.seam.jms;

import java.io.Serializable;
import java.util.Map;

import java.util.Set;
import javax.jms.*;
import javax.enterprise.event.Event;
import org.jboss.solder.exception.control.ExceptionToCatch;
import org.jboss.solder.logging.Logger;

public class QueueBuilderImpl implements QueueBuilder {
    private Logger logger = Logger.getLogger(QueueBuilderImpl.class);
    private Event<ExceptionToCatch> exceptionEvent;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private javax.jms.MessageProducer messageProducer;
    private javax.jms.MessageConsumer messageConsumer;
    private Queue lastQueue;
    private boolean transacted = false;
    private int sessionMode = Session.AUTO_ACKNOWLEDGE;

    QueueBuilderImpl(Event<ExceptionToCatch> exceptionEvent) {
        this.exceptionEvent = exceptionEvent;
    }

    @Override
    public QueueBuilder destination(Queue queue) {
        this.lastQueue = queue;
        cleanupMessaging();
        this.messageProducer = null;
        this.messageConsumer = null;
        return this;
    }

    private void cleanupMessaging() {
        try {
            if (this.messageConsumer != null) {
                this.messageConsumer.close();
            }
            if (this.messageProducer != null) {
                this.messageProducer.close();
            }

            this.messageConsumer = null;
            this.messageProducer = null;
        } catch (JMSException ex) {
            logger.error("unable to create queuebuilder, ",ex);
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        }
    }

    private void cleanConnection() {
        try {
            if (this.session != null) {
                this.session.close();
            }
            if (this.connection != null) {
                this.connection.close();
            }

            this.session = null;
            this.connection = null;
            
            cleanupMessaging();
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        }
    }

    private void createMessageProducer() {
        if (messageProducer == null) {
            try {
                this.messageProducer = session.createProducer(lastQueue);
            } catch (JMSException ex) {
                this.exceptionEvent.fire(new ExceptionToCatch(ex));
            }
        }
    }

    private void createMessageConsumer() {
        if (messageConsumer == null) {
            try {
                this.messageConsumer = session.createConsumer(lastQueue);
            } catch (JMSException ex) {
                this.exceptionEvent.fire(new ExceptionToCatch(ex));
            }
        }
    }

    @Override
    public QueueBuilder connectionFactory(ConnectionFactory cf) {
        try {
            cleanConnection();
            this.connectionFactory = cf;
            this.connection = cf.createConnection();
            this.session = connection.createSession(transacted, sessionMode);
            logger.debug("Created session  "+session);
            this.connection.start();
            //getSession();
            return this;
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
            return null;
        }
    }

    @Override
    public QueueBuilder send(Message m) {
        this.createMessageProducer();
        try{
            this.messageProducer.send(m);
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        }
        return this;
    }

    @Override
    public QueueBuilder sendMap(Map map) {
        try {
            MapMessage msg = this.session.createMapMessage();
            Set<Object> keys = map.keySet();
            for (Object key : keys) {
                Object value = map.get(key);
                msg.setObject(key.toString(), value);
            }
            send(msg);
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        }
        return this;
    }

    @Override
    public QueueBuilder sendString(String string) {
        try{
            TextMessage tm = this.session.createTextMessage();
            tm.setText(string);
            send(tm);
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        } 
        return this;
    }
    
    @Override
    public QueueBuilder sendObject(Serializable obj) {
        try{
            ObjectMessage om = this.session.createObjectMessage();
            om.setObject(obj);
            send(om);
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        } 
        return this;
    }

    @Override
    public QueueBuilder listen(MessageListener listener) {
        this.createMessageConsumer();
        try{
            this.messageConsumer.setMessageListener(listener);
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
        }
        return this;
    }

    @Override
    public QueueBuilder newBuilder() {
        return new QueueBuilderImpl(this.exceptionEvent);
    }

    @Override
    public QueueBuilder transacted() {
        this.transacted = !this.transacted;
        return this;
    }

    @Override
    public QueueBuilder sessionMode(int sessionMode) {
        this.sessionMode = sessionMode;
        return this;
    }
    
    public QueueBrowser getQueueBrowser() {
        try{
            return this.session.createBrowser(this.lastQueue);
        } catch (JMSException ex) {
            this.exceptionEvent.fire(new ExceptionToCatch(ex));
            return null;
        }
    }
}
