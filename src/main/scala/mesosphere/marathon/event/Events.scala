package mesosphere.marathon.event

import mesosphere.marathon.api.v1.AppDefinition
import mesosphere.marathon.health.HealthCheck
import org.rogach.scallop.ScallopConf
import com.google.inject.{Singleton, Provides, AbstractModule}
import com.google.common.eventbus.{AsyncEventBus, EventBus}
import java.util.concurrent.Executors
import javax.inject.Named
import java.util.logging.Logger

trait EventSubscriber[C <: ScallopConf, M <: AbstractModule] {
  def configuration(): Class[C]
  def module(): Option[Class[M]]
}

//TODO(FL): Wire this up such that events are optional.
trait EventConfiguration extends ScallopConf {

  lazy val eventSubscriber = opt[String]("event_subscriber",
    descr = "e.g. http_callback",
    required = false,
    noshort = true)
}

class EventModule(conf: EventConfiguration) extends AbstractModule {

  val log = Logger.getLogger(getClass.getName)
  def configure() {}

  @Named(EventModule.busName)
  @Provides
  @Singleton
  def provideEventBus(): Option[EventBus] = {
    if (conf.eventSubscriber.isSupplied) {
      log.info("Creating Event Bus.")
      val pool =  Executors.newCachedThreadPool()
      val bus = new AsyncEventBus(pool)
      Some(bus)
    } else {
      None
    }
  }
}

object EventModule {
  final val busName = "events"
}

sealed trait MarathonEvent {
  def eventType = "marathon_event"
}

case class ApiPostEvent(
  clientIp: String,
  uri: String,
  appDefinition: AppDefinition,
  override val eventType: String = "api_post_event"
) extends MarathonEvent

case class MesosStatusUpdateEvent(
  taskId: String,
  taskStatus: Int,
  appID: String,
  host: String,
  ports: Iterable[Integer],
  override val eventType: String = "status_update_event"
) extends MarathonEvent

// event subscriptions

sealed trait MarathonSubscriptionEvent extends MarathonEvent {
  def clientIp: String
  def callbackUrl: String
  override def eventType = "marathon_subscription_event"
}

case class Subscribe(
  clientIp: String,
  callbackUrl: String,
  override val eventType: String = "subscribe_event"
) extends MarathonSubscriptionEvent

case class Unsubscribe(
  clientIp: String,
  callbackUrl: String,
  override val eventType: String = "unsubscribe_event"
) extends MarathonSubscriptionEvent

// health checks

sealed trait MarathonHealthCheckEvent extends MarathonEvent {
  override def eventType = "marathon_health_check_event"
}

case class AddHealthCheck(
  healthCheck: HealthCheck,
  override val eventType: String = "add_health_check_event"
) extends MarathonHealthCheckEvent

case class RemoveHealthCheck(
  appId: String,
  override val eventType: String = "remove_health_check_event"
) extends MarathonHealthCheckEvent

case class FailedHealthCheck(
  appId: String,
  taskId: String,
  healthCheck: HealthCheck
) extends MarathonHealthCheckEvent
