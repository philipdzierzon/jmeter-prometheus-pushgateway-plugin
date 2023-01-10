package com.github.johrstrom.listener.updater;

import com.github.johrstrom.collector.JMeterCollectorRegistry;
import com.github.johrstrom.listener.ListenerCollectorConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;

/**
 * The Updater family of classes are meant to update the actual Collectors given the configuration. The main problem it
 * tries to solve is tying a Prometheus Collector (with a type like 'Historgram', labels, etc.) to the JMeter data that
 * collector is measuring.
 * <p>
 * Note: This class assumes that the Collector object passed into the constructor is valid. I.e., it is not null and
 * registered. Of course, being null has much more serious consequences.
 *
 * @author Jeff Ohrstrom
 */
public abstract class AbstractUpdater {

    private static final String NULL = "null";

    protected ListenerCollectorConfig config;
    protected static final JMeterCollectorRegistry registry = JMeterCollectorRegistry.getInstance();

    // helper lookup table for sample variables, so we don't loop over arrays every update.
    private Map<String, Integer> varIndexLookup;

    /**
     * All subclasses should have this and only this constructor signature.
     *
     * @param config the configuration of the collector
     */
    protected AbstractUpdater(ListenerCollectorConfig config) {
        this.config = config;
        this.buildVarLookup();
    }


    /**
     * Updates the collector it was instantiated with the given event.
     *
     * @param event SampleEvent
     */
    public abstract void update(SampleEvent event);

    public static class AssertionContext {

        public final AssertionResult assertion;
        public final SampleEvent event;

        public AssertionContext(AssertionResult a, SampleEvent e) {
            this.assertion = a;
            this.event = e;
        }
    }

    /**
     * Helper function to extract the label values from the Sample Event. Values depend on how the Updater was
     * configured.
     *
     * @param event SampleEvent
     * @return the label values.
     */
    protected String[] labelValues(SampleEvent event) {
        String[] labels = config.getLabels();
        String[] values = new String[labels.length];
        JMeterVariables vars = JMeterContextService.getContext().getVariables();

        for (int i = 0; i < labels.length; i++) {
            String name = labels[i];
            String value = null;

            // reserved keyword for the sampler's label (the name)
            if (name.equalsIgnoreCase("label")) {
                value = event.getResult().getSampleLabel();

            } else if (name.equalsIgnoreCase("code")) {    // code also reserved
                value = event.getResult().getResponseCode();

                // try to find it as a plain'ol variable.
            } else if (this.varIndexLookup.get(name) != null) {
                int idx = this.varIndexLookup.get(name);
                value = event.getVarValue(idx);

                // lastly look in sample_variables
            } else if (vars != null) {
                value = vars.get(name);
            }

            values[i] = (value == null || value.isEmpty()) ? NULL : value;
        }

        return values;
    }

    protected String[] labelValues(AssertionContext ctx) {
        String[] labels = config.getLabels();
        String[] values = new String[labels.length];
        JMeterVariables vars = JMeterContextService.getContext().getVariables();

        for (int i = 0; i < labels.length; i++) {
            String name = labels[i];
            String value = null;

            if (name.equalsIgnoreCase("label")) {
                value = ctx.assertion.getName();

                // try to find it as a plain'ol variable.
            } else if (this.varIndexLookup.get(name) != null) {
                int idx = this.varIndexLookup.get(name);
                value = ctx.event.getVarValue(idx);

                // lastly look in sample_variables
            } else if (vars != null) {
                value = vars.get(name);
            }

            values[i] = (value == null || value.isEmpty()) ? NULL : value;
        }

        return values;
    }


    private void buildVarLookup() {
        this.varIndexLookup = new HashMap<>();

        for (int i = 0; i < SampleEvent.getVarCount(); i++) {
            String name = SampleEvent.getVarName(i);
            if (inLabels(name)) {
                this.varIndexLookup.put(name, i);
            }
        }

    }

    private boolean inLabels(String searchFor) {
        String[] labels = config.getLabels();
        for (String label : labels) {
            if (label.equalsIgnoreCase(searchFor)) {
                return true;
            }
        }

        return false;
    }

}
