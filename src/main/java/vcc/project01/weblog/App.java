package vcc.project01.weblog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.net.HttpURLConnection;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.rapidoid.http.Req;
import org.rapidoid.setup.On;
import org.rapidoid.web.config.listener.GenericRouteConfigListener;

import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroSerde;
import com.hortonworks.registries.schemaregistry.serdes.avro.kafka.KafkaAvroSerializer;

import vcc.project01.kafkaConnectHdfs.ProducerCreator;
import vcc.project01.weblog.ReadPropertyFile;
import static com.hortonworks.registries.schemaregistry.serdes.avro.AvroSnapshotSerializer.SERDES_PROTOCOL_VERSION;
import static com.hortonworks.registries.schemaregistry.serdes.avro.SerDesProtocolHandlerRegistry.METADATA_ID_VERSION_PROTOCOL;


@SuppressWarnings("serial")
public class App {
	private static JSONArray configArray = null;
	private static Schema schema = null;
	private static JSONObject jsonObject = null;
	private static Properties prop = ReadPropertyFile.readPropertyFile();
	private static String file_avsc = prop.getProperty("file_avsc");
	private static String folder = prop.getProperty("folder");
	private static String path_web_server = prop.getProperty("path_web_server");
	private static String host_config_service = prop.getProperty("host_config_service");
	private static String path_config_service = prop.getProperty("path_config_service");
	private static String path_avsc_service = prop.getProperty("path_avsc_service");
	private static String time_second_to_update_schema = prop.getProperty("time_second_to_update_schema");

	/**
	 * Push data that included in parameters of requests to Producer. The requests
	 * are received by webserver. Check type, format and optional of data before
	 * push them to Producer. Each data that Producer received will be send to
	 * Topics. After Topics received enough data, all record that stored in Topics
	 * will store to file .avro on HDFS.
	 * 
	 * @param req
	 * @param filePath
	 * @param configArray
	 * @param schema
	 * @param producer
	 * @return
	 * @throws Exception
	 */
	public static Object pushDataHDFS(Req req, String filePath, JSONArray configArray, Schema schema,
			Producer producer) throws Exception {
		Map<String, String> queryParams = req.params();
		Iterator<String> it = queryParams.keySet().iterator();
		
		Properties props = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(new File("kafka-producer.props"))) {
            props.load(fileInputStream);
        }

		// Check if the field required is available. If not will skip.
		if (!checkOptional(queryParams, configArray)) {
			return 2;
		}

		// All required field is available
		try {

			GenericRecord avroRecord = new GenericData.Record(schema);

			while (it.hasNext()) {
				String keyRecord = it.next();
				String valueRecord = queryParams.get(keyRecord);
				System.out.println("*** Key :" + keyRecord + "  Value :" + queryParams.get(keyRecord));

				// Check if data is valid
				if (checkDataConfig(configArray, keyRecord, valueRecord).getFirst()) {
					// Change value of data to the right type
					switch (checkDataConfig(configArray, keyRecord, valueRecord).getSecond()) {
					case "Integer":
						avroRecord.put(keyRecord, Integer.parseInt(valueRecord));
						break;
					case "Long":
						avroRecord.put(keyRecord, Long.parseLong(valueRecord));
						break;
					default:
						avroRecord.put(keyRecord, valueRecord);
						break;
					}
				} else {
					System.out.println("Throw this data.");
					return 3;
				}
			
			}
			
			// If data is valid then send it to Topic by Producer
			// And Kafka connect will receive data in topic and put to HDFS file .avro
			ProducerRecord<String, GenericRecord> record = new ProducerRecord<>("rest_test2", avroRecord);
			try {
				producer.send(record);
				System.out.println("Pushing data completed.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return 1;
	}

	private static Object jsonToAvro(String jsonString, Schema schema) throws Exception {
		DatumReader<Object> reader = new GenericDatumReader<>(schema);
		Object object = reader.read(null, DecoderFactory.get().jsonDecoder(schema, jsonString));

		if (schema.getType().equals(Schema.Type.STRING)) {
			object = object.toString();
		}
		return object;
	}
	
	/**
	 * Check data is valid before pushing to HDFS for each field
	 * 
	 * @param configArray
	 * @param keyRecord
	 * @param valueRecord
	 * @return
	 */
	public static MyResult checkDataConfig(JSONArray configArray, String keyRecord, String valueRecord) {
		for (Object item : configArray) {
			JSONObject field = (JSONObject) item;
			if (keyRecord.equals(field.get("name"))) {
				String type = (String) field.get("type");
				String sformat = (String) field.get("sformat");
				// Check type and format of data
				if (checkType(type, valueRecord) && checkFormat(sformat, valueRecord)) {
					return new MyResult(true, type);
				} else {
					return new MyResult(false, "");
				}
			}
			continue;
		}
		System.out.println("Data is invalid.");
		return new MyResult(false, "");
	}

	/**
	 * Check type of data is valid. Currently there are 3 type: Integer, Long,
	 * String.
	 * 
	 * @param type
	 * @param valueRecord
	 * @return boolean
	 */
	public static boolean checkType(String type, String valueRecord) {
		switch (type) {
		case "Integer":
			try {
				Integer.parseInt(valueRecord);
			} catch (Exception e) {
				System.out.println("Data must be Integer.");
				return false;
			}
			break;
		case "Long":
			try {
				Long.parseLong(valueRecord);
			} catch (Exception e) {
				System.out.println("Data must be Long.");
				return false;
			}
			break;
		default:
			break;
		}
		return true;
	}

	/**
	 * Check format of data with regex is valid.
	 * 
	 * @param sformat
	 * @param valueRecord
	 * @return boolean
	 */
	public static boolean checkFormat(String sformat, String valueRecord) {
		Pattern pattern = Pattern.compile(sformat);
		if (pattern.matcher(valueRecord).matches()) {
			return true;
		} else {
			System.out.println("Data Format is incorrect.");
			return false;
		}
	}

	/**
	 * Check optional of data if it is required. If this field is requied, it must
	 * be existed in parameters.
	 * 
	 * @param queryParams
	 * @param configArray
	 * @return boolean
	 */
	public static boolean checkOptional(Map<String, String> queryParams, JSONArray configArray) {
		for (Object item : configArray) {
			JSONObject field = (JSONObject) item;
			if (field.get("optional").equals("required") && !queryParams.containsKey(field.get("name"))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Send Request to Config_Server for read file config and .avsc from HDFS.
	 * Response: Content of file config or .avsc
	 * 
	 * @param path
	 * @return StringBuffer
	 * @throws IOException
	 */
	public static StringBuffer getFileFromConfigService(String path) throws IOException {
		String url = host_config_service + path;
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		BufferedReader bufin = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
		while ((inputLine = bufin.readLine()) != null) {
			response.append(inputLine);
		}
		bufin.close();
		return response;
	}

	// Main func
	public static void main(String[] args) throws IOException {
		On.port(19080);
		JSONParser parser = new JSONParser();
		String file = file_avsc;
		String filePath = folder + file;

		ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
		Runnable task = () -> {
			try {
				System.out.println("Load file Config and Avsc");
				StringBuffer response_config = new StringBuffer();
				StringBuffer response_avsc = new StringBuffer();
				try {
					response_config = getFileFromConfigService(path_config_service);
					response_avsc = getFileFromConfigService(path_avsc_service);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				System.out.println("Response_config:   " + response_config);
				System.out.println("Response_avsc:   " + response_avsc);

				jsonObject = (JSONObject) parser.parse(response_config.toString());
				configArray = (JSONArray) jsonObject.get("data");
				schema = new Schema.Parser().parse(response_avsc.toString());

			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		};

		executor.scheduleWithFixedDelay(task, 0, Long.parseLong(time_second_to_update_schema), TimeUnit.SECONDS);
		Producer<String, GenericRecord> producer = ProducerCreator.createProducer();

		On.get(path_web_server).plain((Req req) -> pushDataHDFS(req, filePath, configArray, schema, producer));

	}
}

/**
 * This class for type of return: +, First: boolean - True if data is valid. +,
 * Second: String - Return type of this data.
 * 
 * @author hoang
 */
final class MyResult {
	private final boolean checkData;
	private final String dataType;

	public MyResult(boolean checkData, String dataType) {
		this.checkData = checkData;
		this.dataType = dataType;
	}

	public boolean getFirst() {
		return checkData;
	}

	public String getSecond() {
		return dataType;
	}
}
