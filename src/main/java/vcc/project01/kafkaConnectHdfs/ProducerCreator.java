package vcc.project01.kafkaConnectHdfs;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroSerde;
import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroSerializer;

import static com.hortonworks.registries.schemaregistry.serdes.avro.AvroSnapshotSerializer.SERDES_PROTOCOL_VERSION;
import static com.hortonworks.registries.schemaregistry.serdes.avro.SerDesProtocolHandlerRegistry.METADATA_ID_VERSION_PROTOCOL;

/**
 * Setup some properties for connect to Kafka and Schema Registry.
 * @author hoang
 *
 */
public class ProducerCreator {
	/**
	 * Setup properties for connecting to Kafka and Schema Registry.
	 * @return KafkaProducer
	 */
	public static KafkaProducer createProducer() {
		Properties props = new Properties();
		try (FileInputStream fileInputStream = new FileInputStream(new File("kafka-producer.props"))) {
            props.load(fileInputStream);
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		props.put(SERDES_PROTOCOL_VERSION, METADATA_ID_VERSION_PROTOCOL);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(KafkaAvroSerializer.STORE_SCHEMA_VERSION_ID_IN_HEADER, "true");
        props.put(KafkaAvroSerde.VALUE_SCHEMA_VERSION_ID_HEADER_NAME, "value.schema.version.id");
		
		
		return new KafkaProducer<>(props);
		
	}
}