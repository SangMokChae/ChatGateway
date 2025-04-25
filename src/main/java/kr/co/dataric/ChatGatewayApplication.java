package kr.co.dataric;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChatGatewayApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(ChatGatewayApplication.class);
	}
	
}

/***
 * Mongo 27018 사용 Open
 * CMD -> DBPath = C:\Java\mongo\db
 *  ----->
 *
 * 해당 변경
 * 1. mongod --port 27017 --replSet rs0 --dbpath="C:\Java\mongo\db0"
 * 2. mongod --port 27018 --replSet rs0 --dbpath="C:\Java\mongo\db1" --> mongo 27017, 27018 port를 연결
 *
 * --> mongosh "mongodb://localhost:27017,localhost:27018/?replicaSet=rs0" 로 접속하여 27017, 27018을 실행한다.
 
 * ZOOKEEPER 실행 명령어
 * CMD -> CD /kafka
 * .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
 * KAFKA 실행
 * CMD -> CD /kafka
 * .\bin\windows\kafka-server-start.bat config\server.properties
 */