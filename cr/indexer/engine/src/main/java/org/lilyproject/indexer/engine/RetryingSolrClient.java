package org.lilyproject.indexer.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.SolrException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * Puts a wrapper around SolrClient that will pause and retry on certain kinds of errors. This avoids
 * records being skipped from indexing and logs flooding with error stack traces in case of a (temporary)
 * SOLR connection problem.
 */
public class RetryingSolrClient {

    public static SolrClient create(SolrClient solrClient) {
        RetryingSolrClientInvocationHandler handler = new RetryingSolrClientInvocationHandler(solrClient);
        return (SolrClient)Proxy.newProxyInstance(SolrClient.class.getClassLoader(), new Class[] { SolrClient.class },
                handler);
    }

    private static class RetryingSolrClientInvocationHandler implements InvocationHandler {
        private SolrClient solrClient;
        private Log log = LogFactory.getLog("org.lilyproject.indexer.solrconnection");

        public RetryingSolrClientInvocationHandler(SolrClient solrClient) {
            this.solrClient = solrClient;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            int attempt = 0;
            while (true) {
                try {
                    return method.invoke(solrClient, args);
                } catch (InvocationTargetException ite) {
                    Throwable throwable = ite.getTargetException();

                    Throwable cause = throwable.getCause();

                    if (throwable instanceof SolrException) {
                        // Get the HTTP status code
                        int code = ((SolrException)throwable).code();
                        if (code == 404) {
                            // The user has probably configured an incorrect path in the SOLR URL
                            int pause = getBackOff(attempt);
                            log.error("'Not Found' exception connecting to SOLR " + solrClient.getDescription() +
                                    ". Incorrect path in SOLR URL? Will sleep " + pause +
                                    "ms and retry (attempt " + attempt + ")");
                            Thread.sleep(pause);
                        } else {
                            throw throwable;
                        }
                    } else if (cause != null && cause instanceof UnknownHostException) {
                        int pause = getBackOff(attempt);
                        log.error("SOLR host unknown " + solrClient.getDescription() + ". Will sleep " + pause +
                                "ms and retry (attempt " + attempt + ")");
                        Thread.sleep(pause);
                    } else if (throwable.getCause() != null && throwable.getCause() instanceof ConnectException) {
                        int pause = getBackOff(attempt);
                        log.error("Could not connect to SOLR " + solrClient.getDescription() +
                                ". Will sleep " + pause + "ms and retry (attempt " + attempt + ")");
                        Thread.sleep(pause);
                    } else {
                        throw throwable;
                    }
                }
                attempt++;
            }
        }

        private int getBackOff(int attempt) {
            if (attempt < 10) {
                return 500;
            } else {
                return 3000;
            }
        }
    }
}