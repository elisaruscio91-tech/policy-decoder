package decoder.backend.config

import com.typesafe.config.{Config, ConfigFactory}
import zio.*

final case class AnthropicCfg(apiKey: String, workspaceId: Option[String], model: String)
final case class OpenRouterCfg(apiKey: String, model: String)
final case class OllamaCfg(baseUrl: String, apiKey: Option[String], model: String)
final case class AiCfg(provider: String, anthropic: AnthropicCfg, openrouter: OpenRouterCfg, ollama: OllamaCfg)

final case class AppConfig(
  port: Int,
  jwtSecret: String,
  databaseUrl: String,
  ai: AiCfg,
  defaultMonthlyQuota: Int,
  embeddingModel: String,
)

object AppConfig:

  private def opt(c: Config, path: String): Option[String] =
    if c.hasPath(path) && c.getString(path).nonEmpty then Some(c.getString(path)) else None

  def load: Task[AppConfig] = ZIO.attempt {
    val c = ConfigFactory.load()
    AppConfig(
      port = c.getInt("app.port"),
      jwtSecret = c.getString("app.jwt-secret"),
      databaseUrl = c.getString("db.url"),
      ai = AiCfg(
        provider = c.getString("ai.provider"),
        anthropic = AnthropicCfg(
          apiKey = opt(c, "ai.anthropic.api-key").getOrElse(""),
          workspaceId = opt(c, "ai.anthropic.workspace-id"),
          model = c.getString("ai.anthropic.model"),
        ),
        openrouter = OpenRouterCfg(
          apiKey = opt(c, "ai.openrouter.api-key").getOrElse(""),
          model = c.getString("ai.openrouter.model"),
        ),
        ollama = OllamaCfg(
          baseUrl = opt(c, "ai.ollama.base-url").getOrElse(""),
          apiKey = opt(c, "ai.ollama.api-key"),
          model = c.getString("ai.ollama.model"),
        ),
      ),
      defaultMonthlyQuota = c.getInt("quota.default-monthly"),
      embeddingModel = c.getString("embedding.model"),
    )
  }

  val layer: ZLayer[Any, Throwable, AppConfig] = ZLayer.fromZIO(load)
