package com.github.johrstrom.config;

import com.github.johrstrom.collector.BaseCollectorConfig;
import com.github.johrstrom.collector.CollectorElement;
import io.prometheus.client.Collector;
import java.util.Map.Entry;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.threads.JMeterVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusMetricsConfig extends CollectorElement<BaseCollectorConfig>
        implements NoThreadClone, TestStateListener {

    private static final long serialVersionUID = 7602510312126862226L;

    private final Logger log = LoggerFactory.getLogger(PrometheusMetricsConfig.class);

    @Override
    public void testEnded() {
        this.setRunningVersion(false);
        JMeterVariables variables = getThreadContext().getVariables();

        for (Entry<BaseCollectorConfig, Collector> entry : this.collectors.entrySet()) {
            BaseCollectorConfig cfg = entry.getKey();
            variables.remove(cfg.getMetricName());
        }

        this.clearCollectors();
    }

    @Override
    public void testEnded(String arg0) {
        this.testEnded();
    }

    @Override
    public void testStarted() {
        this.setRunningVersion(true);
        this.makeNewCollectors();
        JMeterVariables variables = getThreadContext().getVariables();

        log.debug("Test started, adding {} collectors to variables", this.collectors.size());

        for (Entry<BaseCollectorConfig, Collector> entry : this.collectors.entrySet()) {
            BaseCollectorConfig cfg = entry.getKey();
            variables.putObject(cfg.getMetricName(), entry.getValue());
            log.debug("Added ({},{}) to variables.", entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void testStarted(String arg0) {
        this.testStarted();
    }

    @Override
    public PrometheusMetricsConfig clone() {
        PrometheusMetricsConfig clone = new PrometheusMetricsConfig();
        clone.setCollectorConfigs(this.getCollectorConfigs());

        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PrometheusMetricsConfig) {
            PrometheusMetricsConfig other = (PrometheusMetricsConfig) o;

            CollectionProperty thisConfig = this.getCollectorConfigs();
            CollectionProperty otherConfig = other.getCollectorConfigs();
            boolean sameSize = thisConfig.size() == otherConfig.size();

            for (int i = 0; i < thisConfig.size(); i++) {
                JMeterProperty left = thisConfig.get(i);
                JMeterProperty right = otherConfig.get(i);

                if (!left.equals(right)) {
                    return false;
                }
            }

            return sameSize;
        }

        return false;
    }

}
