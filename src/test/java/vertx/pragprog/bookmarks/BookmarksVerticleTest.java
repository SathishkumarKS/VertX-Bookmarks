package vertx.pragprog.bookmarks;

import static org.mockito.Mockito.when;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import vertx.pragprog.bookmarks.dao.BookmarkDao;

@RunWith(VertxUnitRunner.class)
public class BookmarksVerticleTest {

	int port = 8888;
	private Vertx vertx;

	@Mock(name = "BookmarkDao")
	BookmarkDao mockDao;
	@InjectMocks
	BookmarksVerticle bmVerticle;

	@Before
	public void setUp(TestContext context) {
		MockitoAnnotations.initMocks(this);

		vertx = Vertx.vertx();
		DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
		vertx.deployVerticle(bmVerticle, options, context.asyncAssertSuccess());
	}

	@After
	public void tearDown(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
	}

	@Test
	public void test_get_bookmark(TestContext context) {
		final Async async = context.async();

		String id = "1";
		String requestUri = BookmarksVerticle.BOOKMARK_URL + "/" + id;

		when(mockDao.getBookmark(Matchers.anyString())).thenReturn(new Bookmark(id, "url", "Vert.x Reactive Framework"));

		vertx.createHttpClient().getNow(port, "localhost", requestUri, 
				response -> {
					context.assertEquals(200, response.statusCode());
					response.bodyHandler(body -> {
						Bookmark bm = Json.decodeValue(body.toString(), Bookmark.class);
						context.assertEquals("1", bm.getBookmarkId());
						context.assertEquals("Vert.x Reactive Framework", bm.getBookmarkTitle());
						async.complete();
					});					
				});
	}

	@Test
	public void test_get_bookmark_not_found(TestContext context) {
		final Async async = context.async();

		String id = "2112";
		String requestUri = BookmarksVerticle.BOOKMARK_URL + "/" + id;
		
		when(mockDao.getBookmark(Matchers.anyString())).thenReturn(null);

		vertx.createHttpClient().getNow(port, "localhost", requestUri, 
				response -> {
					context.assertEquals(410, response.statusCode());
					async.complete();
				});
	}
	
	@Test
	public void test_get_bookmark_exception(TestContext context) {
		final Async async = context.async();

		String id = "1";
		String requestUri = BookmarksVerticle.BOOKMARK_URL + "/" + id;
		
		when(mockDao.getBookmark(Matchers.anyString())).thenThrow(new RuntimeException("testing when things go bad"));

		vertx.createHttpClient().getNow(port, "localhost", requestUri, 
				response -> {
					context.assertEquals(500, response.statusCode());
					response.handler(body -> {
						context.assertEquals("testing when things go bad", body.toString());
						async.complete();
					});					
				});
	}
	
	
	@Test
	public void test_add_bookmark(TestContext context) {
		final Async async = context.async();

		String requestUri = BookmarksVerticle.BOOKMARK_URL;

		when(mockDao.addBookmark(Matchers.anyObject())).thenReturn("5150");

		final String json = Json.encodePrettily(new Bookmark(null, "nodejs.org", "Node.js"));

		vertx.createHttpClient().post(port, "localhost", requestUri)
			.putHeader("content-type", "application/json")
			.putHeader("content-length", Integer.toString(json.length()))
			.handler(response -> {
				context.assertEquals(201, response.statusCode());
				response.bodyHandler(body -> {
					context.assertEquals("/bookmarks/5150", body.toString());
					async.complete();
				});	
			}).write(json).end();
	}
	
	@Test
	public void test_add_bookmark_invalid_json(TestContext context) {
		final Async async = context.async();

		String requestUri = BookmarksVerticle.BOOKMARK_URL;

		when(mockDao.addBookmark(Matchers.anyObject())).thenReturn("5150");

		final String json = "\"bookmarkTitle\" : \"Node.js\"";
		
		vertx.createHttpClient().post(port, "localhost", requestUri)
			.putHeader("content-type", "application/json")
			.putHeader("content-length", Integer.toString(json.length()))
			.handler(response -> {
				context.assertEquals(400, response.statusCode());
				response.bodyHandler(body -> {
					context.assertEquals("invalid JSON", body.toString());
					async.complete();
				});	
			}).write(json).end();
	}
	
	@Test
	public void test_add_bookmark_exception(TestContext context) {
		final Async async = context.async();

		String requestUri = BookmarksVerticle.BOOKMARK_URL;

		when(mockDao.addBookmark(Matchers.anyObject())).thenThrow(new RuntimeException("testing when things go bad"));

		final String json = Json.encodePrettily(new Bookmark(null, "nodejs.org", "Node.js"));
		
		vertx.createHttpClient().post(port, "localhost", requestUri)
			.putHeader("content-type", "application/json")
			.putHeader("content-length", Integer.toString(json.length()))
			.handler(response -> {
				context.assertEquals(500, response.statusCode());
				response.bodyHandler(body -> {
					context.assertEquals("testing when things go bad", body.toString());
					async.complete();
				});	
			}).write(json).end();
	}

}
