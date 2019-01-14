package io.vertx.ch2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

public class HttpServerVerticle extends AbstractVerticle {

  private static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  private static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create(vertx);
  private String wikiDbQueue;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    System.out.println("Http server called");
    wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);
    router.get("/").handler(this::indexHandler);
    /*router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post().handler(BodyHandler.create());
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);*/

    int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
    server.requestHandler(router)
      .listen(portNumber, ar -> {
        if(ar.succeeded()) {
          startFuture.complete();
        } else {
          startFuture.fail(ar.cause());
        }
      });
  }

  private void indexHandler(RoutingContext context) {
    System.out.println("index handler called");
    DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

    vertx.eventBus().send(wikiDbQueue, new JsonObject(), options, reply -> {
      if(reply.succeeded()){
        JsonObject body = (JsonObject) reply.result().body();
        JsonObject data = new JsonObject();
        data.put("title", "Wiki Home");
        data.put("pages", body.getJsonArray("pages").getList());
        templateEngine.render(data, "templates/index.ftl", ar -> {
          if(ar.succeeded()){
            context.response().putHeader("Content-Type", "text/html");
            context.response().end(ar.result());
          } else {
            context.fail(ar.cause());
          }
        });
      } else {
        context.fail(reply.cause());
      }
    });
  }
}
