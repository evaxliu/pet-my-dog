package sparkCode;

import mysql.DBUtils;
import mysql.Sql2oModel;
import com.google.gson.Gson;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.mapper.ObjectMapper;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.pac4j.core.config.Config;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.oidc.client.OidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.pac4j.sparkjava.CallbackRoute;
import org.pac4j.sparkjava.SecurityFilter;
import org.pac4j.sparkjava.SparkHttpActionAdapter;
import org.pac4j.sparkjava.SparkWebContext;
import spark.*;
import org.sql2o.Sql2o;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import static spark.Spark.*;

public class Main {
    private static DBUtils.Model model;
    private static final String HOST = "localhost";
    private static final int PORT_ONE = 9200;
    private static final String SCHEME = "http";
    private static final String INDEX = "location";
    private static RestHighLevelClient restHighLevelClient;
    private static final long LOCATION_EXPIRE_TIME = 1000 * 60 * 15;

    public static void main(String[] args) {
        staticFileLocation("/public");
        port(80); // Spark will run on port 80
        /*****************************************     Begin OAuth config     *****************************************/

        // Setup google oauth api configuration with pac4j
        final OidcConfiguration oidcConfiguration = new OidcConfiguration();
        oidcConfiguration.setClientId("205317701531-od80vq0biekitm7bq8irtfoen3fhpfo0.apps.googleusercontent.com");
        oidcConfiguration.setSecret("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");
        oidcConfiguration.setDiscoveryURI("https://accounts.google.com/.well-known/openid-configuration");
        oidcConfiguration.setUseNonce(true);
        oidcConfiguration.addCustomParam("prompt", "consent");

        // Create client with the configuration
        final OidcClient oidcClient = new OidcClient(oidcConfiguration);
        oidcClient.setAuthorizationGenerator((ctx, session, profile) -> {
            profile.addRole("ROLE_ADMIN");
            return Optional.of(profile);
        });
        oidcClient.setCallbackUrl("http://petmydog.fun/callback");

        // Security configuration using google client
        Config config = new Config(oidcClient);
        config.addAuthorizer("custom", new CustomAuthorizer());
        config.setHttpActionAdapter(new DemoHttpActionAdapter());

        // Set up call back end points
        CallbackRoute callback = new CallbackRoute(config, "http://petmydog.fun/map-view/find-dogs", true);
        get("/callback", callback);
        post("/callback", callback);

        /*******************************************     End OAuth config     *****************************************/


        /*****************************************     Begin SQL config     *****************************************/

        Sql2o sql2o = new Sql2o("jdbc:mysql://34.70.199.136:3306/pmd", "root", "b4lIbLjGOvszgcLC");
        model = new Sql2oModel(sql2o);

        /*****************************************     END SQL config     *****************************************/

        /*************************************   Start Elasticsearch config   *****************************************/
        restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost(HOST, PORT_ONE, SCHEME)));

        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(INDEX);
        try {
            restHighLevelClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        CreateIndexRequest createIndexRequest = new CreateIndexRequest(INDEX);
        try {
            restHighLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        PutMappingRequest putMappingRequest = new PutMappingRequest(INDEX);

        Map<String, Object> type = new HashMap<>();
        type.put("type", "geo_point");
        Map<String, Object> keyType = new HashMap<>();
        keyType.put("type", "keyword");
        Map<String, Object> timeType = new HashMap<>();
        timeType.put("type", "long");

        Map<String, Object> properties = new HashMap<>();
        properties.put("location", type);
        properties.put("uid", keyType);
        properties.put("timestamp", timeType);

        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("properties", properties);
        putMappingRequest.source(jsonMap);

        try {
            restHighLevelClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            System.out.println(e.getMessage());

        }

        /***************************************   End Elasticsearch config   *****************************************/



        /*******************************************   Start Security Guard   *****************************************/

        before("/hello", new SecurityFilter(config, "GoogleClient"));
        before("/updateLocation", new SecurityFilter(config, "GoogleClient"));

        after("/callback", (Request request, Response response) -> {
            List<UserProfile> users = getProfiles(request, response);
            if (users.size() == 1) {
                UserProfile user = users.get(0);
                if (model.getUser(user.getId()) == null) {
                    String email = null;
                    String pic_link = null;
                    String username = null;
                    Map<String, Object> attributes = user.getAttributes();
                    if (attributes.containsKey("given_name") && attributes.get("given_name") != null) {
                        username = attributes.get("given_name").toString();
                    }
                    if (attributes.containsKey("email") && attributes.get("email") != null) {
                        email = attributes.get("email").toString();
                    }
                    if (attributes.containsKey("picture") && attributes.get("picture") != null) {
                        pic_link = attributes.get("picture").toString();
                    }
                    if (!model.createUser(new DBUtils.User(user.getId(), username,
                            null, email, null, pic_link, "1000-01-01 00:00:00", null))) {
                        response.redirect("/login", 500);
                    }
                }
            } else {
                response.redirect("/login");
            }

        });

        /******************************************    End Security Guard     *****************************************/


        /********************************************   Start CORS config   *******************************************/

//        final Map<String, String> corsHeaders = new HashMap<>();
//        corsHeaders.put("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS");
//        corsHeaders.put("Access-Control-Allow-Origin", "*");
//        corsHeaders.put("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,");
//        corsHeaders.put("Access-Control-Allow-Credentials", "true");
//
//        Filter filter = new Filter() {
//            @Override
//            public void handle(Request request, Response response) throws Exception {
//                corsHeaders.forEach((key, value) -> {
//                    response.header(key, value);
//                });
//            }
//        };
//        Spark.after(filter);

        /********************************************   End CORS config   *********************************************/



        /****************************************   Start Service end pints   *****************************************/

        get("/login", (req, res) -> {
            final SparkWebContext context = new SparkWebContext(req, res);
            HttpAction action;
            try {
                action = oidcClient.getRedirectionAction(context, JEESessionStore.INSTANCE).get();
            } catch (final HttpAction e) {
                action = e;
            }
            SparkHttpActionAdapter.INSTANCE.adapt(action, context);
            return null;
        });

        get("/profile", Main::getUserProfile);

        get("/getDogProfile", Main::getDogProfile);

        post("/newDog", Main::createDogProfile);

        post("/requestMeetup", Main::requestMeetup);

        post("/acceptMeetup", Main::acceptMeetup);

        post("/rejectMeetup", Main::rejectMeetup);

        post("/getNearbyUser", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                Gson gson = new Gson();
                DBUtils.Location loc = gson.fromJson(request.body(), DBUtils.Location.class);
                SearchSourceBuilder srb = new SearchSourceBuilder();
                QueryBuilder qb = QueryBuilders.geoDistanceQuery(INDEX)
                        .point(loc.getLat(), loc.getLng())
                        .distance("500", DistanceUnit.METERS);
                srb.query(qb);

                SearchRequest searchRequest = new SearchRequest(INDEX);
                searchRequest.source(srb);
                List<String> result = new ArrayList<>();
                try {
                    SearchResponse search = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
                    SearchHits hits = search.getHits();
                    for (SearchHit hit: hits) {
                        String location = hit.getSourceAsString();
                        DBUtils.UserLocation jsonObject = gson.fromJson(location, DBUtils.UserLocation.class);
                        if (System.currentTimeMillis() - jsonObject.getTimestamp() < LOCATION_EXPIRE_TIME)
                            result.add(jsonObject.getUid());
                    }
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
                return gson.toJson(result);
            }
        });

        post("/updateLocation", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                String uid = getUserId(request, response);
                Gson gson = new Gson();
                DBUtils.Location loc = gson.fromJson(request.body(), DBUtils.Location.class);

                // Update ES location
                long timestamp = System.currentTimeMillis();
                Map<String,Object> jsonMap = new HashMap<>();
                jsonMap.put("location", new GeoPoint(loc.getLat(), loc.getLng()));
                jsonMap.put("uid", uid);
                jsonMap.put("timestamp", timestamp);

                IndexRequest indexRequest = new IndexRequest(INDEX).id(uid).source(jsonMap);
                try {
                    restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                    return gson.toJson("failed to update");
                }
                return gson.toJson("updated");
            }
        });

        /******************************************   End Service end pints   *****************************************/
    }

    /****************************************   Start utils   *****************************************/
    private static List<UserProfile> getProfiles(Request request, Response response) {
        final SparkWebContext context = new SparkWebContext(request, response);
        final ProfileManager manager = new ProfileManager(context, JEESessionStore.INSTANCE);
        return manager.getProfiles();
    }

    private static String getUserProfile(Request request, Response response) {
        try {
            Gson gson = new Gson();
            String uid = request.queryParams("uid");
            DBUtils.User user = model.getUser(uid);
            List<DBUtils.Dog> dogs = model.getDogsFromUserId(uid);
            user.setDogs(dogs);
            return gson.toJson(user);
        } catch (Exception e) {
            halt(500);
            return null;
        }
    }

    private static String createDogProfile(Request request, Response response) {
        Gson gson = new Gson();
        model.createDog(gson.fromJson(request.body(), DBUtils.Dog.class));
        return gson.toJson("Success");
    }

    private static String requestMeetup(Request request, Response response) {
        Gson gson = new Gson();
        String body = request.body();
        Map<String, String> bodyContent = gson.fromJson(body, Map.class);
        // check if users exist
        DBUtils.User sender = model.getUser(bodyContent.get("sender"));
        DBUtils.User receiver = model.getUser(bodyContent.get("receiver"));
        if (sender == null || receiver == null) {
            return gson.toJson("User not found");
        }

        // insert meeting information
        UUID id = UUID.randomUUID();
        model.createMeetUp(new DBUtils.MeetUp(id.toString(),
                bodyContent.get("sender"), bodyContent.get("receiver"), "Pending"));
        return gson.toJson(id.toString());
    }

    private static String acceptMeetup(Request request, Response response) {
        return meetupRespond(request, "Accepted");
    }

    private static String rejectMeetup(Request request, Response response) {
        return meetupRespond(request, "Rejected");
    }

    private static String meetupRespond(Request request, String status) {
        Gson gson = new Gson();
        String body = request.body();
        Map<String, String> bodyContent = gson.fromJson(body, Map.class);
        String mid = bodyContent.get("mid");
        DBUtils.MeetUp m = model.getMeetUp(mid);
        if (m == null) {
            return gson.toJson("Meetup does not exist");
        }
        model.updateMeetUp(mid, status);
        return gson.toJson("Success");
    }

    private static String getDogProfile(Request request, Response response) {
        Gson gson = new Gson();
        String body = request.body();
        Map<String, String> bodyContent = gson.fromJson(body, Map.class);
        return gson.toJson(model.getDog(bodyContent.get("dogId")));
    }

    private static String getUserId(Request request, Response response) {
        List<UserProfile> users = getProfiles(request, response);
        if (users.size() == 1) {
            UserProfile user = users.get(0);
            return user.getId();
        }
        return null;
    }
    /****************************************   End utils   *****************************************/

}