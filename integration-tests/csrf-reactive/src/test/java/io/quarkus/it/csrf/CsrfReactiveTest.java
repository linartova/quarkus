package io.quarkus.it.csrf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Base64;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.Cookie;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;

@QuarkusTest
public class CsrfReactiveTest {

    @Test
    public void testCsrfTokenInForm() throws Exception {
        try (final WebClient webClient = createWebClient()) {
            webClient.addRequestHeader("Authorization", basicAuth("alice", "alice"));
            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenForm");

            assertEquals("CSRF Token Form Test", htmlPage.getTitleText());

            HtmlForm loginForm = htmlPage.getForms().get(0);

            loginForm.getInputByName("name").setValueAttribute("alice");

            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            TextPage textPage = loginForm.getInputByName("submit").click();

            assertEquals("alice:true:tokenHeaderIsSet=false", textPage.getContent());

            textPage = webClient.getPage("http://localhost:8081/service/hello");
            assertEquals("hello", textPage.getContent());

            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testCsrfTokenWithFormRead() throws Exception {
        try (final WebClient webClient = createWebClient()) {

            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenWithFormRead");

            assertEquals("CSRF Token With Form Read Test", htmlPage.getTitleText());

            HtmlForm loginForm = htmlPage.getForms().get(0);

            loginForm.getInputByName("name").setValueAttribute("alice");

            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            TextPage textPage = loginForm.getInputByName("submit").click();

            assertEquals("verified:true:tokenHeaderIsSet=false", textPage.getContent());

            textPage = webClient.getPage("http://localhost:8081/service/hello");
            assertEquals("hello", textPage.getContent());

            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testCsrfTokenInFormButNoCookie() throws Exception {
        try (final WebClient webClient = createWebClient()) {
            webClient.addRequestHeader("Authorization", basicAuth("alice", "alice"));
            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenForm");

            assertEquals("CSRF Token Form Test", htmlPage.getTitleText());

            HtmlForm loginForm = htmlPage.getForms().get(0);

            loginForm.getInputByName("name").setValueAttribute("alice");
            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            webClient.getCookieManager().clearCookies();

            assertNull(webClient.getCookieManager().getCookie("csrftoken"));
            try {
                loginForm.getInputByName("submit").click();
                fail("400 status error is expected");
            } catch (FailingHttpStatusCodeException ex) {
                assertEquals(400, ex.getStatusCode());
            }
            webClient.getCookieManager().clearCookies();

        }
    }

    public void testCsrfFailedAuthentication() throws Exception {
        try (final WebClient webClient = createWebClient()) {
            webClient.addRequestHeader("Authorization", basicAuth("alice", "password"));
            try {
                webClient.getPage("http://localhost:8081/service/csrfTokenForm");
                fail("401 status error is expected");
            } catch (FailingHttpStatusCodeException ex) {
                assertEquals(401, ex.getStatusCode());
                assertEquals("true", ex.getResponse().getResponseHeaderValue("test-mapper"));
                assertNull(webClient.getCookieManager().getCookie("csrftoken"));
            }
            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testCsrfTokenInMultipart() throws Exception {
        try (final WebClient webClient = createWebClient()) {

            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenMultipart");

            assertEquals("CSRF Token Multipart Test", htmlPage.getTitleText());

            HtmlForm loginForm = htmlPage.getForms().get(0);

            loginForm.getInputByName("name").setValueAttribute("alice");
            loginForm.getInputByName("file").setValueAttribute("file.txt");

            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            TextPage textPage = loginForm.getInputByName("submit").click();

            assertEquals("alice:true:true:true:tokenHeaderIsSet=false", textPage.getContent());

            textPage = webClient.getPage("http://localhost:8081/service/hello");
            assertEquals("hello", textPage.getContent());

            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testWrongCsrfTokenCookieValue() throws Exception {
        try (final WebClient webClient = createWebClient()) {
            webClient.addRequestHeader("Authorization", basicAuth("alice", "alice"));
            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenForm");

            assertEquals("CSRF Token Form Test", htmlPage.getTitleText());

            HtmlForm loginForm = htmlPage.getForms().get(0);

            loginForm.getInputByName("name").setValueAttribute("alice");
            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            webClient.getCookieManager().clearCookies();

            assertNull(webClient.getCookieManager().getCookie("csrftoken"));

            webClient.getCookieManager().addCookie(new Cookie("localhost", "csrftoken", "wrongvalue"));

            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));
            try {
                loginForm.getInputByName("submit").click();
                fail("400 status error is expected");
            } catch (FailingHttpStatusCodeException ex) {
                assertEquals(400, ex.getStatusCode());
            }
            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testWrongCsrfTokenFormValue() throws Exception {
        try (final WebClient webClient = createWebClient()) {
            webClient.addRequestHeader("Authorization", basicAuth("alice", "alice"));
            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenForm");

            assertEquals("CSRF Token Form Test", htmlPage.getTitleText());

            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            RestAssured.given().urlEncodingEnabled(true)
                    .param("csrf-token", "wrong-value")
                    .post("/service/csrfTokenForm")
                    .then().statusCode(400);

            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testCsrfTokenHeaderValue() throws Exception {
        try (final WebClient webClient = createWebClient()) {

            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenWithHeader");
            assertEquals("CSRF Token Header Test", htmlPage.getTitleText());
            List<DomElement> inputs = htmlPage.getElementsByIdAndOrName("X-CSRF-TOKEN");
            String csrfToken = inputs.get(0).asNormalizedText();

            Cookie csrfCookie = webClient.getCookieManager().getCookie("csrftoken");
            assertNotNull(csrfCookie);

            RestAssured.given()
                    .header("Authorization", basicAuth("alice", "alice"))
                    .header(new Header("X-CSRF-TOKEN", csrfToken))
                    .cookie(csrfCookie.getName(), csrfCookie.getValue())
                    .urlEncodingEnabled(true)
                    .param("csrf-header", "X-CSRF-TOKEN")
                    .post("/service/csrfTokenWithHeader")
                    .then()
                    .body(Matchers.equalTo("verified:true:tokenHeaderIsSet=true"));
            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testCsrfTokenHeaderValueJson() throws Exception {
        try (final WebClient webClient = createWebClient()) {

            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenWithHeader");
            assertEquals("CSRF Token Header Test", htmlPage.getTitleText());
            List<DomElement> inputs = htmlPage.getElementsByIdAndOrName("X-CSRF-TOKEN");
            String csrfToken = inputs.get(0).asNormalizedText();

            Cookie csrfCookie = webClient.getCookieManager().getCookie("csrftoken");
            assertNotNull(csrfCookie);

            RestAssured.given()
                    .header("Authorization", basicAuth("alice", "alice"))
                    .header(new Header("X-CSRF-TOKEN", csrfToken))
                    .cookie(csrfCookie.getName(), csrfCookie.getValue())
                    .header(new Header("Content-Type", "application/json"))
                    .body("{}")
                    .post("/service/csrfTokenWithHeader")
                    .then()
                    .body(Matchers.equalTo("verified:true:tokenHeaderIsSet=true"));

            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testWrongCsrfTokenHeaderValue() throws Exception {
        try (final WebClient webClient = createWebClient()) {

            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenWithHeader");
            assertEquals("CSRF Token Header Test", htmlPage.getTitleText());

            Cookie csrfCookie = webClient.getCookieManager().getCookie("csrftoken");
            assertNotNull(csrfCookie);

            RestAssured.given()
                    .header("Authorization", basicAuth("alice", "alice"))
                    // CSRF cookie is signed, so passing it as a header value will fail
                    .header(new Header("X-CSRF-TOKEN", csrfCookie.getValue()))
                    .cookie(csrfCookie.getName(), csrfCookie.getValue())
                    .urlEncodingEnabled(true)
                    .param("csrf-header", "X-CSRF-TOKEN")
                    .post("/service/csrfTokenWithHeader")
                    .then()
                    .statusCode(400);
            webClient.getCookieManager().clearCookies();
        }
    }

    @Test
    public void testWrongCsrfTokenWithFormRead() throws Exception {
        try (final WebClient webClient = createWebClient()) {

            HtmlPage htmlPage = webClient.getPage("http://localhost:8081/service/csrfTokenWithFormRead");

            assertEquals("CSRF Token With Form Read Test", htmlPage.getTitleText());

            assertNotNull(webClient.getCookieManager().getCookie("csrftoken"));

            RestAssured.given().urlEncodingEnabled(true)
                    .param("csrf-token", "wrong-value")
                    .post("/service/csrfTokenWithFormRead")
                    .then().statusCode(400);

            webClient.getCookieManager().clearCookies();
        }
    }

    private WebClient createWebClient() {
        WebClient webClient = new WebClient();
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        return webClient;
    }

    private String basicAuth(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }
}
