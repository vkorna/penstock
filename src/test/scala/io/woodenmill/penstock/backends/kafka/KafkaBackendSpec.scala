package io.woodenmill.penstock.backends.kafka

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.woodenmill.penstock.LoadRunner
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration._

class KafkaBackendSpec extends FlatSpec with Matchers with EmbeddedKafka with BeforeAndAfterAll with ScalaFutures with Eventually {

  val topic = "input"
  val kafkaPort = 6001
  val kafkaBackend: KafkaBackend = KafkaBackend(s"localhost:$kafkaPort")
  implicit val stringDeserializer = new StringDeserializer()
  implicit val kafkaConfig = EmbeddedKafkaConfig(kafkaPort = kafkaPort)
  implicit val testPatienceConfig: PatienceConfig = PatienceConfig(timeout = 2.seconds)

  override protected def beforeAll(): Unit = {
    EmbeddedKafka.start()(kafkaConfig)
    createCustomTopic(topic)
  }

  override protected def afterAll(): Unit = {
    kafkaBackend.shutdown()
    EmbeddedKafka.stop()
  }


  "Kafka Backend" should "send a message to Kafka" in {
    //given
    val message = new ProducerRecord[Array[Byte], Array[Byte]](topic, "key".getBytes, "value".getBytes)

    //when
    kafkaBackend.send(message)

    //then
    consumeFirstKeyedMessageFrom[String, String](topic) shouldBe("key", "value")
  }

  it should "integrate with Load Runner" in {
    //given
    val system = ActorSystem()
    val mat = ActorMaterializer()(system)
    val message = new ProducerRecord[Array[Byte], Array[Byte]](topic, "from-runner".getBytes)

    //when
    val runnerFinished = LoadRunner(message, 1.milli, 1).run()(kafkaBackend, mat)

    //then
    whenReady(runnerFinished) { _ =>
      consumeFirstStringMessageFrom(topic) shouldBe "from-runner"
    }
  }

  it should "expose basic Kafka Producer metrics" in {
    //given
    val backend = KafkaBackend(s"localhost:$kafkaPort")
    val someMessage = new ProducerRecord[Array[Byte], Array[Byte]](topic, "some message".getBytes)

    //when
    backend.send(someMessage)
    backend.send(someMessage)

    //then
    eventually {
      val metrics = backend.metrics()
      metrics.recordSendTotal.value shouldBe 2
      metrics.recordErrorTotal.value shouldBe 0
    }
  }

}