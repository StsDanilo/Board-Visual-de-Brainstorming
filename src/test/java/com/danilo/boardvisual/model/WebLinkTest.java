package com.danilo.boardvisual.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras do endereço web de um card ({@link WebLink}). */
class WebLinkTest {

    private static String normalized(String input) {
        WebLink.Check check = WebLink.check(input);
        assertTrue(check.isValid(), () -> "deveria aceitar \"" + input + "\": " + check.error());
        return check.link();
    }

    private static String error(String input) {
        WebLink.Check check = WebLink.check(input);
        assertFalse(check.isValid(), () -> "deveria recusar \"" + input + "\", virou " + check.link());
        assertNotNull(check.error());
        assertEquals("", check.link());
        return check.error();
    }

    @Test
    void acceptsHttpAndHttpsAsTheyAre() {
        assertEquals("https://exemplo.com", normalized("https://exemplo.com"));
        assertEquals("http://exemplo.com/a/b?c=1&d=2#topo", normalized("http://exemplo.com/a/b?c=1&d=2#topo"));
        assertEquals("https://localhost:8080/painel", normalized("https://localhost:8080/painel"));
        assertEquals("https://192.168.0.10/x", normalized("https://192.168.0.10/x"));
        assertEquals("https://[::1]:8443/", normalized("https://[::1]:8443/"));
    }

    @Test
    void addsHttpsWhenTheSchemeIsMissing() {
        assertEquals("https://exemplo.com", normalized("exemplo.com"));
        assertEquals("https://www.unit.br/cursos", normalized("www.unit.br/cursos"));
        assertEquals("https://localhost:3000", normalized("localhost:3000"), "site:porta não é esquema");
        assertEquals("https://exemplo.com:8080/a", normalized("exemplo.com:8080/a"));
        assertEquals("https://exemplo.com/x", normalized("//exemplo.com/x"));
    }

    @Test
    void trimsSpacesAndLowersTheScheme() {
        assertEquals("https://Exemplo.com/Caminho", normalized("  HTTPS://Exemplo.com/Caminho \n"));
    }

    @Test
    void emptyMeansNoLink() {
        assertEquals("", normalized(""));
        assertEquals("", normalized("   "));
        assertEquals("", normalized(null));
    }

    @Test
    void rejectsOtherSchemes() {
        for (String input : List.of("javascript:alert(1)", "file:///C:/Windows/notepad.exe", "mailto:a@b.com",
                "ftp://exemplo.com", "data:text/html,oi", "ms-settings:", "vbscript:x", "jar:file:/x!/y")) {
            assertTrue(error(input).contains("http"), input);
        }
    }

    @Test
    void rejectsLocalPathsAndExecutables() {
        for (String input : List.of("C:\\Windows\\System32\\calc.exe", "C:/Users/x/a.bat", "c:", "\\\\servidor\\pasta",
                "/etc/passwd", "../segredo.txt", "pasta\\arquivo.exe", "https://exemplo.com\\..\\x")) {
            WebLink.Check check = WebLink.check(input);
            assertFalse(check.isValid(), input);
        }
        assertEquals(WebLink.ERROR_LOCAL, error("C:\\Windows\\notepad.exe"));
    }

    @Test
    void rejectsSpacesControlCharactersAndLineBreaks() {
        assertEquals(WebLink.ERROR_SPACES, error("https://exemplo.com/um caminho"));
        assertEquals(WebLink.ERROR_SPACES, error("https://exemplo.com/\nlinha"));
        assertEquals(WebLink.ERROR_SPACES, error("https://exemplo.com/\u0000"));
        assertEquals(WebLink.ERROR_SPACES, error("https://exemplo.com/\u00a0x"));
    }

    @Test
    void rejectsIncompleteOrDisguisedAddresses() {
        assertEquals(WebLink.ERROR_NO_DOMAIN, error("ola"), "palavra solta não é site");
        assertEquals(WebLink.ERROR_NO_HOST, error("https://"));
        assertEquals(WebLink.ERROR_NO_HOST, error("https:///caminho"));
        assertEquals(WebLink.ERROR_USER_INFO, error("https://banco.com@golpe.com/"));
        assertEquals(WebLink.ERROR_INVALID, error("https://exemplo.com:99999/"));
        assertEquals(WebLink.ERROR_INVALID, error("https://exemplo..com"));
        assertEquals(WebLink.ERROR_INVALID, error("https://exemplo.com/<script>"));
        assertEquals(WebLink.ERROR_INVALID, error("http:exemplo.com"));
        assertEquals(WebLink.ERROR_INVALID, error("https://exemplo.com/%zz"));
    }

    @Test
    void rejectsTooLongAddresses() {
        String longPath = "https://exemplo.com/" + "a".repeat(WebLink.MAX_LENGTH);
        assertEquals(WebLink.ERROR_TOO_LONG, error(longPath));
        assertEquals("https://exemplo.com/a", normalized("exemplo.com/a"));
    }

    @Test
    void browserFormIsAsciiAndOnlyForValidLinks() {
        assertEquals(Optional.of("https://xn--aa-4iaz.com.br/p%C3%A1gina"),
                WebLink.toBrowserUri("https://açaí.com.br/página"));
        assertEquals(Optional.of("https://exemplo.com"), WebLink.toBrowserUri("exemplo.com"));
        assertEquals(Optional.empty(), WebLink.toBrowserUri(""));
        assertEquals(Optional.empty(), WebLink.toBrowserUri("javascript:alert(1)"));
        assertEquals(Optional.empty(), WebLink.toBrowserUri("file:///C:/x.exe"));
    }

    @Test
    void cardKeepsOnlyValidNormalizedLinks() {
        Card card = new Card(0, 0);
        card.setLink("exemplo.com");
        assertEquals("https://exemplo.com", card.getLink());
        assertTrue(card.hasLink());

        card.setLink("javascript:alert(1)");
        assertEquals("", card.getLink(), "o modelo nunca guarda um link perigoso");
        assertFalse(card.hasLink());

        card.setLink("https://a.com");
        card.setLink(null);
        assertFalse(card.hasLink());
    }
}
