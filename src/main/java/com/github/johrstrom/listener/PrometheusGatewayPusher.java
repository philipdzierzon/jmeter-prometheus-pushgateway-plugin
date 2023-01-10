package com.github.johrstrom.listener;

import com.github.johrstrom.collector.JMeterCollectorRegistry;
import io.prometheus.client.exporter.PushGateway;
import java.io.IOException;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusGatewayPusher {

    private static final Logger log = LoggerFactory.getLogger(PrometheusGatewayPusher.class);
    private static PrometheusGatewayPusher instance = null;

    private static final String PROMETHEUS_IP = "prometheus.ip";
    private static final String PROMETHEUS_IP_DEFAULT = "127.0.0.1:3002";
    private final String prometheusIp = JMeterUtils.getPropDefault(PROMETHEUS_IP,
            PROMETHEUS_IP_DEFAULT);

    private final JMeterCollectorRegistry registry = JMeterCollectorRegistry.getInstance();
    private final PushGateway pushGateway = new PushGateway(prometheusIp);

    public static synchronized PrometheusGatewayPusher getInstance() {
        if (instance == null) {
            log.debug("Creating Prometheus Server");
            instance = new PrometheusGatewayPusher();
        }

        return instance;
    }

    public void pushMetricsToGateway() {

        try {
            log.info("Pushing collected metrics to push gateway");
            pushGateway.pushAdd(registry, "jmeter_job");
        } catch (IOException e) {
            log.error("Couldn't push metrics to push gateway", e);
        }
    }
}
