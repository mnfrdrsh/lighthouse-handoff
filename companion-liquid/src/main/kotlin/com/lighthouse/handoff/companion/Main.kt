package com.lighthouse.handoff.companion

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 31313
    val engine = LiquidEngine()
    
    // In a real scenario, this would be async/suspend but for Ktor setup we can start it in background
    // or just let it initialize synchronously if possible.
    // For this spike, we'll assume the engine loads async
    // runBlocking { engine.initialize() }
    
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
        }
        
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        
        routing {
            get("/health") {
                if (engine.isReady()) {
                    call.respond(HealthResponse("ok", "liquid", engine.modelName, true))
                } else {
                    call.respond(HealthResponse("ok", "liquid", null, false))
                }
            }
            
            post("/analyze") {
                try {
                    val request = call.receive<AnalyzeRequest>()
                    
                    try {
                        val analysis = engine.analyze(request)
                        call.respond(HttpStatusCode.OK, analysis)
                    } catch (e: IllegalStateException) {
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Liquid model is not loaded.", "MODEL_NOT_READY"))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse("Liquid model returned invalid JSON.", "INVALID_MODEL_OUTPUT"))
                    }
                    
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request payload", "BAD_REQUEST"))
                }
            }
        }
    }.start(wait = true)
}
