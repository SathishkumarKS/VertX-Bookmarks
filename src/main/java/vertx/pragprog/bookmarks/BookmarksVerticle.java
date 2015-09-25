package vertx.pragprog.bookmarks;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Collection;

import javax.annotation.Resource;

import vertx.pragprog.bookmarks.dao.BookmarkDao;

public class BookmarksVerticle extends AbstractVerticle {
	private static Logger log = LoggerFactory.getLogger(BookmarksVerticle.class);

	public static final String BOOKMARK_URL = "/bookmarks";
	public static final String CONTENT_TYPE_HEADER = "content-type";
	public static final String JSON_CONTENT = "application/json";

	@Resource
	private BookmarkDao bookmarksDao;

	@Override
	public void start(Future<Void> future) {
		// Create a router which matches HTTP requests
		// to the correct method
		Router router = Router.router(vertx);

		// enables the reading of the request body
		router.route(BOOKMARK_URL + "*").handler(BodyHandler.create());

		// URL mapping
		router.get(BOOKMARK_URL).handler(this::getAllBookmarks);
		router.get(BOOKMARK_URL + "/:id").handler(this::getBookmark);
		router.post(BOOKMARK_URL).handler(this::addBookmark);

		// start HTTP Listener
		this.startHttp(router, future);
	}

	/**
	 * get all the bookmarks
	 */
	protected void getAllBookmarks(RoutingContext routingContext) {
		// never call blocking operations
		// directly from an event loop
		vertx.executeBlocking(
			this::asynchRetrieveAllBookmarks, 
			asynchResult -> {
				// check the result
				if (asynchResult.succeeded()) {
					Collection<Bookmark> bookmarks = (Collection<Bookmark>) asynchResult.result();
					if (bookmarks != null && bookmarks.size() > 0) {
						// return as JSON
						routingContext.response()
							.setStatusCode(200)
							.putHeader(CONTENT_TYPE_HEADER, JSON_CONTENT)
							.end(Json.encodePrettily(bookmarks));
					}
					else {
						// there is no content
						routingContext.response().setStatusCode(204).end();
					}
				}
				else {
					// error during retrieval
					String errText = asynchResult.cause().getMessage();
					log.error(errText);
					routingContext.response().setStatusCode(500).end(errText);
				}
			});
	}

	/**
	 * get a bookmark
	 */
	protected void getBookmark(RoutingContext routingContext) {
		// get the Bookmark from the HTTP URL
		final String id = routingContext.request().getParam("id");

		if (id != null) {
			log.info("attempting to get the Bookmark (" + id + ")");
			// never call blocking operations
			// directly from an event loop
			vertx.executeBlocking(
					future -> {
						this.asynchRetrieveBookmark(future, id);
					}, 
					asynchResult -> {
						// when the retrival of Bookmark completes
						// 	we need to return the JSON
						if (asynchResult.succeeded()) {
							Bookmark bm = (Bookmark) asynchResult.result();
							this.handleBookmarkResponse(routingContext, bm);
						}
						else {
							// error during retrieval
							String errText = asynchResult.cause().getMessage();
							log.error(errText);
							routingContext.response().setStatusCode(500).end(errText);
						}
					});
		}
		else {
			// if there is no id then it is a bad request
			routingContext.response().setStatusCode(400).end();
		}
	}

	/**
	 * add a new bookmark
	 */
	protected void addBookmark(RoutingContext routingContext) {

		final Bookmark bm = this.decodeJsonToBookmark(routingContext.getBodyAsString());
		if (bm!=null){
			vertx.executeBlocking(
					future -> {
						this.asynchAddBookmark(future, bm);
					},
					asynchResult -> {
						if (asynchResult.succeeded()) {
							// Return the URL to the new bookmark
							String id = (String) asynchResult.result();
							routingContext.response()
								.setStatusCode(201)
								.putHeader(CONTENT_TYPE_HEADER, JSON_CONTENT)
								.end(BOOKMARK_URL + "/" + id);
						}
						else {
							// error during retrieval
							String errText = asynchResult.cause().getMessage();
							log.error(errText);
							routingContext.response().setStatusCode(500).end(errText);
						}
					});
		}
		else {
			// invalid JSON
			routingContext.response().setStatusCode(400).end("invalid JSON");			
		}
	}

	protected Bookmark decodeJsonToBookmark(String json){
		Bookmark bm = null;
		try {
			bm = Json.decodeValue(json,Bookmark.class);
		}
		catch(Exception e){
			log.warn("invalid JSON:" + json);
		}
		return bm;
	}
	
	protected void asynchAddBookmark(Future<Object> future, Bookmark bm) {
		log.info("aynch attempting to add a Bookmark");

		try {
			String bmId = bookmarksDao.addBookmark(bm);
			future.complete(bmId);
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			future.fail(e);
		}
	}

	protected void asynchRetrieveAllBookmarks(Future<Object> future) {
		log.info("aynch attempting to get all the Bookmarks");

		try {
			Collection<Bookmark> bookmarks = bookmarksDao.getAllBookmarks();
			future.complete(bookmarks);
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			future.fail(e);
		}
	}

	protected void asynchRetrieveBookmark(Future<Object> future, String id) {
		log.info("aynch attempting to get the Bookmark (" + id + ")");

		try {
			Bookmark bm = bookmarksDao.getBookmark(id);
			future.complete(bm);
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			future.fail(e);
		}
	}

	protected void handleBookmarkResponse(RoutingContext routingContext, Bookmark bm) {
		if (bm != null) {
			routingContext.response()
				.setStatusCode(200)
				.putHeader(CONTENT_TYPE_HEADER, JSON_CONTENT)
				.end(Json.encodePrettily(bm));
		}
		else {
			routingContext.response().setStatusCode(410).end();
		}
	}

	protected void startHttp(Router router, Future<Void> future) {
		int port = config().getInteger("http.port", 8080);

		log.info("starting the HTTP server on port " + port);

		vertx.createHttpServer().requestHandler(router::accept).listen(port, result -> {
			if (result.succeeded()) {
				future.complete();
			}
			else {
				future.fail(result.cause());
			}
		});
	}
}
