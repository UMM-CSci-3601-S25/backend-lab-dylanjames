package umm3601.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.validation.Validation;
import io.javalin.validation.Validator;
import umm3601.todo.Todo;
import umm3601.todo.TodoController;

/**
 * Tests the logic of the UserController
 *
 * @throws IOException
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them names would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
class TodoControllerSpec {
  private TodoController todoController;

  // An instance of the controller we're testing that is prepared in
  // `setupEach()`, and then exercised in the various tests below.

  // A Mongo object ID that is initialized in `setupEach()` and used
  // in a few of the tests. It isn't used all that often, though,
  // which suggests that maybe we should extract the tests that
  // care about it into their own spec file?
  private ObjectId samsId;

  // The client and database that will be used
  // for all the tests in this spec file.
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> todoCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  /**
   * Sets up (the connection to the) DB once; that connection and DB will
   * then be (re)used for all the tests, and closed in the `teardown()`
   * method. It's somewhat expensive to establish a connection to the
   * database, and there are usually limits to how many connections
   * a database will support at once. Limiting ourselves to a single
   * connection that will be shared across all the tests in this spec
   * file helps both speed things up and reduce the load on the DB
   * engine.
   */
  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    // Reset our mock context and argument captor (declared with Mockito
    // annotations @Mock and @Captor)
    MockitoAnnotations.openMocks(this);

    // Setup database
    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
        new Document()
            .append("owner", "Blanche")
            .append("category", "homework")
            .append("status", "true"));
     testTodos.add(
        new Document()
            .append("owner", "Fry")
            .append("category", "video games")
            .append("status", "false"));
            testTodos.add(
              new Document()
                  .append("owner", "Dawn")
                  .append("category", "homework")
                  .append("status", "true")
                  .append("body", "do 3601 homework"));
    samsId = new ObjectId();
    Document sam = new Document()
        .append("_id", samsId)
        .append("owner", "Sam")
        .append("status", true)
        .append("category", "homework");

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(sam);

    todoController = new TodoController(db);
  }

  @Test
  void canGetAllTodos() throws IOException {

    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());
    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    System.err.println(db.getCollection("todos").countDocuments());
    System.err.println(todoArrayListCaptor.getValue().size());

    assertEquals(
        db.getCollection("todos").countDocuments(),
        todoArrayListCaptor.getValue().size());
  }

  @Test
  void canGetTodosWithCategory() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {"true"}));
    queryParams.put(TodoController.SORT_ORDER_KEY, Arrays.asList(new String[] {"desc"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("true");
    when(ctx.queryParam(TodoController.SORT_ORDER_KEY)).thenReturn("desc");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals("true", todo.category);
    }
  }

  @Test
  void canGetTodosWithOwner() throws IOException {
    String targetOwner = "Fry";
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {targetOwner}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn("Fry");

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.OWNER_KEY, String.class, targetOwner);

    when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwner, todo.owner);
    }
  }

  @Test
  void canGetTodosWithBody() throws IOException {
    String targetOwner = "do 3601 homework";
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.BODY_KEY, Arrays.asList(new String[] {targetOwner}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.BODY_KEY)).thenReturn("do 3601 homework");

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.OWNER_KEY, String.class, targetOwner);

    when(ctx.queryParamAsClass(TodoController.BODY_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwner, todo.body);
    }
  }

  @Test
  void canGetTodosWithStatus() throws IOException {
    Boolean targetOwner = true;
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(String.valueOf(targetOwner)));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn(String.valueOf(targetOwner));

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(
      TodoController.STATUS_KEY,
      String.class,
      String.valueOf(targetOwner)
    );

    when(ctx.queryParamAsClass(TodoController.STATUS_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetOwner, todo.status);
    }
  }

  @Test
  void canGetTodosWithCategoryLowercase() throws IOException {
    String targetCategory = "homework";
    Map<String, List<String>> queryParams = new HashMap<>();

    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {targetCategory}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("homework");

    Validation validation = new Validation();
    Validator<String> validator = validation.validator(TodoController.CATEGORY_KEY, String.class, targetCategory);

    when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class)).thenReturn(validator);

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the users passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals(targetCategory, todo.category);
    }
  }

  @Test
  void getTodoWithBadId() throws IOException {
    when(ctx.pathParam("id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodos(ctx);
    });

    assertEquals("The requested Todo id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  @Test
  void getTodoWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodos(ctx);
    });

    assertEquals("The requested Todo was not found", exception.getMessage());

  }


}
