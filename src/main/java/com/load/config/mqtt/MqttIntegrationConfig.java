package com.load.config.mqtt;

import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@EnableIntegration
@ConditionalOnProperty(prefix = "etl.mqtt", name = "enabled", havingValue = "true")
public class MqttIntegrationConfig {

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(EtlMqttProperties properties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {properties.getBrokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);

        if (hasText(properties.getUsername())) {
            options.setUserName(properties.getUsername());
        }
        if (hasText(properties.getPassword())) {
            options.setPassword(properties.getPassword().toCharArray());
        }

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageProducer mqttInbound(
            EtlMqttProperties properties,
            MqttPahoClientFactory mqttClientFactory,
            MessageChannel mqttInputChannel
    ) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                inboundClientId(properties),
                mqttClientFactory,
                properties.commandStartTopic()
        );
        adapter.setCompletionTimeout(5000);
        adapter.setQos(1);

        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
        converter.setPayloadAsBytes(true);
        adapter.setConverter(converter);
        adapter.setOutputChannel(mqttInputChannel);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutputChannel")
    public MessageHandler mqttOutbound(EtlMqttProperties properties, MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                outboundClientId(properties),
                mqttClientFactory
        );
        handler.setAsync(true);
        handler.setDefaultQos(1);

        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
        converter.setPayloadAsBytes(false);
        handler.setConverter(converter);

        return handler;
    }

    private static String inboundClientId(EtlMqttProperties properties) {
        return baseClientId(properties) + "-in";
    }

    private static String outboundClientId(EtlMqttProperties properties) {
        return baseClientId(properties) + "-out";
    }

    private static String baseClientId(EtlMqttProperties properties) {
        if (hasText(properties.getClientId())) {
            return properties.getClientId();
        }
        return properties.getServiceName() + "-" + UUID.randomUUID();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
