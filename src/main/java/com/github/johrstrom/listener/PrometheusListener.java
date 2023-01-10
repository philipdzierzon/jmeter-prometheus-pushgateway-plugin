/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.johrstrom.listener;

import com.github.johrstrom.collector.CollectorElement;
import com.github.johrstrom.collector.JMeterCollectorRegistry;
import com.github.johrstrom.listener.updater.AbstractUpdater;
import com.github.johrstrom.listener.updater.AggregatedTypeUpdater;
import com.github.johrstrom.listener.updater.CountTypeUpdater;
import io.prometheus.client.Collector;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main test element listener class of this library. Jmeter updates this class through the SampleListener interface
 * and it in turn updates the CollectorRegistry. This class is also a TestStateListener to control when it starts up or
 * shuts down the server that ultimately serves Prometheus the results through an http api.
 *
 * @author Jeff Ohrstrom
 */
public class PrometheusListener extends CollectorElement<ListenerCollectorConfig>
        implements SampleListener, Serializable, TestStateListener, NoThreadClone {

    private static final long serialVersionUID = -4833646252357876746L;

    private static final Logger log = LoggerFactory.getLogger(PrometheusListener.class);

    private static final String PROMETHEUS_PUSH_ON_SAMPLE_OCCURRED = "prometheus.pushOnSampleOccurred";
    private static final boolean PROMETHEUS_PUSH_ON_SAMPLE_OCCURRED_DEFAULT = false;
    private final boolean pushOnSampleOccurred = JMeterUtils.getPropDefault(PROMETHEUS_PUSH_ON_SAMPLE_OCCURRED,
            PROMETHEUS_PUSH_ON_SAMPLE_OCCURRED_DEFAULT);
    private final transient PrometheusGatewayPusher prometheusGatewayPusher = PrometheusGatewayPusher.getInstance();

    private List<AbstractUpdater> updaters;

    /*
     * (non-Javadoc)
     *
     * @see org.apache.jmeter.samplers.SampleListener#sampleOccurred(org.apache.
     * jmeter.samplers.SampleEvent)
     */
    @Override
    public void sampleOccurred(SampleEvent event) {

        for (AbstractUpdater updater : this.updaters) {
            updater.update(event);
        }

        if (pushOnSampleOccurred) {
            prometheusGatewayPusher.pushMetricsToGateway();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.jmeter.samplers.SampleListener#sampleStarted(org.apache.jmeter
     * .samplers.SampleEvent)
     */
    @Override
    public void sampleStarted(SampleEvent arg0) {
        // do nothing
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.jmeter.samplers.SampleListener#sampleStopped(org.apache.jmeter
     * .samplers.SampleEvent)
     */
    @Override
    public void sampleStopped(SampleEvent arg0) {
        // do nothing
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.jmeter.testelement.TestStateListener#testEnded()
     */
    @Override
    public void testEnded() {
        prometheusGatewayPusher.pushMetricsToGateway();
        this.clearCollectors();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.jmeter.testelement.TestStateListener#testEnded(java.lang.
     * String)
     */
    @Override
    public void testEnded(String arg0) {
        this.testEnded();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.jmeter.testelement.TestStateListener#testStarted()
     */
    @Override
    public void testStarted() {
        // update the configuration
        this.makeNewCollectors();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.jmeter.testelement.TestStateListener#testStarted(java.lang.
     * String)
     */
    @Override
    public void testStarted(String arg0) {
        this.testStarted();
    }

    @Override
    protected void makeNewCollectors() {
        if (this.registry == null) {
            log.warn("Collector registry has not yet been initialized, doing it now");
            registry = JMeterCollectorRegistry.getInstance();
        }
        this.updaters = new ArrayList<>();

        CollectionProperty collectorDefs = this.getCollectorConfigs();

        for (JMeterProperty collectorDef : collectorDefs) {

            try {
                ListenerCollectorConfig config = (ListenerCollectorConfig) collectorDef.getObjectValue();
                log.debug("Creating collector from configuration: {}", config);
                Collector collector = this.registry.getOrCreateAndRegister(config);
                AbstractUpdater updater = null;

                switch (config.getMeasuringAsEnum()) {
                    case CountTotal:
                    case FailureTotal:
                    case SuccessTotal:
                    case SuccessRatio:
                        updater = new CountTypeUpdater(config);
                        break;
                    case ResponseSize:
                    case ResponseTime:
                    case Latency:
                    case IdleTime:
                    case ConnectTime:
                        updater = new AggregatedTypeUpdater(config);
                        break;
                    default:
                        // hope our IDEs are telling us to use all possible enums!
                        log.error(
                                "{} triggered default case, which means there's no functionality for this and is likely a bug",
                                config.getMeasuringAsEnum());
                        break;
                }

                this.collectors.put(config, collector);
                this.updaters.add(updater);
                log.debug("added {} to list of collectors", config.getMetricName());

            } catch (Exception e) {
                log.error("Didn't create new collector because of error, ", e);
            }

        }

    }

}
