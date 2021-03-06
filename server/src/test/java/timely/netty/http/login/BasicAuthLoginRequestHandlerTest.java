package timely.netty.http.login;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import timely.Configuration;
import timely.api.query.request.BasicAuthLoginRequest;
import timely.auth.AuthCache;
import timely.netty.Constants;
import timely.netty.http.HttpQueryDecoder;
import timely.test.CaptureChannelHandlerContext;
import timely.test.TestConfiguration;

public class BasicAuthLoginRequestHandlerTest {

    private static class TestHttpQueryDecoder extends HttpQueryDecoder {

        public TestHttpQueryDecoder(Configuration config) {
            super(config);
        }

        @Override
        public void decode(ChannelHandlerContext ctx, FullHttpRequest msg, List<Object> out) throws Exception {
            super.decode(ctx, msg, out);
        }

    }

    @ClassRule
    public static final TemporaryFolder temp = new TemporaryFolder();

    private List<Object> results = new ArrayList<>();

    @BeforeClass
    public static void before() throws Exception {
        File conf = temp.newFile("pre-config.properties");
        conf.deleteOnExit();
        TestConfiguration cfg = TestConfiguration.createMinimalConfigurationForTest();
        cfg.toConfiguration(conf);
        Configuration config = new Configuration(conf);
        AuthCache.setSessionMaxAge(config);
    }

    @AfterClass
    public static void after() throws Exception {
        AuthCache.resetSessionMaxAge();
    }

    @Before
    public void setup() throws Exception {
        results.clear();
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        File conf = temp.newFile("config.properties");
        conf.deleteOnExit();
        TestConfiguration cfg = TestConfiguration.createMinimalConfigurationForTest();
        cfg.toConfiguration(conf);
        Configuration config = new Configuration(conf);

        // @formatter:off
		String form = 
        "{\n" +
		"    \"username\": \"test\",\n" +
        "    \"password\": \"test1\"\n" +
		"}";
		// @formatter:on
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/login");
        request.content().writeBytes(form.getBytes());

        TestHttpQueryDecoder decoder = new TestHttpQueryDecoder(config);
        decoder.decode(null, request, results);
        Assert.assertEquals(1, results.size());
        Object result = results.iterator().next();
        Assert.assertEquals(BasicAuthLoginRequest.class, result.getClass());

        BasicAuthLoginRequestHandler handler = new BasicAuthLoginRequestHandler(config);
        CaptureChannelHandlerContext ctx = new CaptureChannelHandlerContext();
        handler.channelRead(ctx, (BasicAuthLoginRequest) result);
        Assert.assertNotNull(ctx.msg);
        Assert.assertTrue(ctx.msg instanceof DefaultFullHttpResponse);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) ctx.msg;
        Assert.assertEquals(HttpResponseStatus.TEMPORARY_REDIRECT, response.getStatus());
        Assert.assertTrue(response.headers().contains(Names.LOCATION));
        Assert.assertEquals(TestConfiguration.GRAFANA_HTTP_ADDRESS_DEFAULT, response.headers().get(Names.LOCATION));
        Assert.assertTrue(response.headers().contains(Names.CONTENT_TYPE));
        Assert.assertEquals(Constants.JSON_TYPE, response.headers().get(Names.CONTENT_TYPE));
        Assert.assertTrue(response.headers().contains(Names.SET_COOKIE));
        Cookie c = ClientCookieDecoder.STRICT.decode(response.headers().get(Names.SET_COOKIE));
        Assert.assertEquals(TestConfiguration.TIMELY_HTTP_ADDRESS_DEFAULT, c.domain());
        Assert.assertEquals(86400, c.maxAge());
        Assert.assertTrue(c.isHttpOnly());
        Assert.assertTrue(c.isSecure());
        Assert.assertEquals(Constants.COOKIE_NAME, c.name());
        UUID.fromString(c.value());
    }

    @Test
    public void testBasicAuthenticationFailure() throws Exception {
        File conf = temp.newFile("config2.properties");
        conf.deleteOnExit();
        TestConfiguration cfg = TestConfiguration.createMinimalConfigurationForTest();
        cfg.toConfiguration(conf);
        Configuration config = new Configuration(conf);

        // @formatter:off
		String form = 
        "{\n" +
		"    \"username\": \"test\",\n" +
        "    \"password\": \"test2\"\n" +
		"}";
		// @formatter:on
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/login");
        request.content().writeBytes(form.getBytes());

        TestHttpQueryDecoder decoder = new TestHttpQueryDecoder(config);
        decoder.decode(null, request, results);
        Assert.assertEquals(1, results.size());
        Object result = results.iterator().next();
        Assert.assertEquals(BasicAuthLoginRequest.class, result.getClass());

        BasicAuthLoginRequestHandler handler = new BasicAuthLoginRequestHandler(config);
        CaptureChannelHandlerContext ctx = new CaptureChannelHandlerContext();
        handler.channelRead(ctx, (BasicAuthLoginRequest) result);
        Assert.assertNotNull(ctx.msg);
        Assert.assertTrue(ctx.msg instanceof DefaultFullHttpResponse);
        DefaultFullHttpResponse response = (DefaultFullHttpResponse) ctx.msg;
        Assert.assertEquals(HttpResponseStatus.UNAUTHORIZED, response.getStatus());
        Assert.assertTrue(response.headers().contains(Names.CONTENT_TYPE));
        Assert.assertEquals(Constants.JSON_TYPE, response.headers().get(Names.CONTENT_TYPE));
    }

}
