package com.danilo.boardvisual.model;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regras do endereço web (link externo) de um card. Java puro, sem JavaFX.
 *
 * O app entrega o endereço ao sistema para abrir no navegador, então só passa
 * o que é claramente uma página web:
 * <ul>
 *   <li>só {@code http://} e {@code https://}; sem esquema, assume {@code https://}
 *       ({@code exemplo.com} vira {@code https://exemplo.com});</li>
 *   <li>recusa outros esquemas ({@code file:}, {@code javascript:}, {@code mailto:}...),
 *       caminhos do computador ({@code C:\...}, {@code \\servidor}, {@code /pasta}),
 *       espaços e quebras de linha, usuário/senha antes do site ({@code https://a@b}),
 *       site sem domínio ({@code https://ola}) e endereços longos demais.</li>
 * </ul>
 * A mesma regra vale ao digitar, ao abrir um arquivo (link adulterado é
 * descartado) e logo antes de abrir o navegador.
 */
public final class WebLink {

    /** Tamanho máximo aceito (navegadores aceitam mais, mas um link de card não precisa). */
    public static final int MAX_LENGTH = 2000;

    /** "esquema:" no começo (letras, dígitos, +, -, .), como na RFC 3986. */
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):(.*)$", Pattern.DOTALL);
    /** O que vem depois de "nome:" quando é uma porta ({@code localhost:8080/x}), e não um esquema. */
    private static final Pattern PORT_AFTER_COLON = Pattern.compile("^\\d+([/?#].*)?$", Pattern.DOTALL);
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*$", Pattern.DOTALL);
    private static final Pattern HOST_CHARS = Pattern.compile("^[A-Za-z0-9.-]+$");

    static final String ERROR_SPACES = "O endereço não pode ter espaços nem quebras de linha.";
    static final String ERROR_LOCAL = "Caminhos de arquivos do computador não são aceitos. "
            + "Use um endereço da web (http:// ou https://).";
    static final String ERROR_TOO_LONG = "O endereço é longo demais (máximo de " + MAX_LENGTH + " caracteres).";
    static final String ERROR_USER_INFO = "Endereços com usuário ou senha antes do site (\"…@site\") não são aceitos.";
    static final String ERROR_NO_HOST = "Falta o site no endereço (ex.: https://exemplo.com).";
    static final String ERROR_NO_DOMAIN = "Endereço incompleto: falta o domínio do site (ex.: exemplo.com).";
    static final String ERROR_INVALID = "Este endereço não é válido.";

    private WebLink() {
    }

    /**
     * Resultado da verificação: {@code link} normalizado (vazio = sem link) ou
     * {@code error} com a mensagem para o usuário.
     */
    public record Check(String link, String error) {
        public boolean isValid() {
            return error == null;
        }

        static Check ok(String link) {
            return new Check(link, null);
        }

        static Check fail(String error) {
            return new Check("", error);
        }
    }

    /**
     * Confere e normaliza o que o usuário digitou. Vazio (ou só espaços) é
     * válido e quer dizer "sem link".
     */
    public static Check check(String input) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty()) {
            return Check.ok("");
        }
        if (value.length() > MAX_LENGTH) {
            return Check.fail(ERROR_TOO_LONG);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || Character.isSpaceChar(c)) {
                return Check.fail(ERROR_SPACES);
            }
        }
        if (value.startsWith("//") && !value.startsWith("///")) {
            value = "https:" + value; // "//exemplo.com": sem esquema
        } else if (value.startsWith("/") || value.startsWith("\\") || value.contains("\\") || DRIVE.matcher(value).matches()) {
            return Check.fail(ERROR_LOCAL);
        }

        String scheme = schemeOf(value);
        if (scheme == null) {
            value = "https://" + value;
            scheme = "https";
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (!lower.equals("http") && !lower.equals("https")) {
            return Check.fail("Só endereços da web são aceitos (http:// ou https://); \""
                    + abbreviate(scheme) + ":\" não é permitido.");
        }
        value = lower + value.substring(scheme.length());
        if (!value.startsWith(lower + "://")) {
            return Check.fail(ERROR_INVALID);
        }
        if (value.length() > MAX_LENGTH) {
            return Check.fail(ERROR_TOO_LONG);
        }
        String error = asciiForm(value).error;
        return error == null ? Check.ok(value) : Check.fail(error);
    }

    /** O link normalizado, ou vazio se a entrada não é aceita (usado pelo modelo e ao abrir arquivos). */
    public static String normalizeOrEmpty(String input) {
        Check check = check(input);
        return check.isValid() ? check.link() : "";
    }

    /**
     * Forma do endereço entregue ao navegador e ao PDF: só ASCII (site com
     * acento convertido para a forma {@code xn--}, demais caracteres com {@code %}).
     * Vazio se o link não passa na verificação: nada estranho chega ao sistema.
     */
    public static Optional<String> toBrowserUri(String link) {
        Check check = check(link);
        if (!check.isValid() || check.link().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(asciiForm(check.link()).uri);
    }

    // ------------------------------------------------------------ detalhes

    /**
     * Esquema no começo do texto, ou {@code null} se não há (inclusive
     * "site:porta", que parece esquema mas não é).
     */
    private static String schemeOf(String value) {
        Matcher m = SCHEME.matcher(value);
        if (!m.matches()) {
            return null;
        }
        if (PORT_AFTER_COLON.matcher(m.group(2)).matches()) {
            return null;
        }
        return m.group(1);
    }

    private record Ascii(String uri, String error) {
    }

    /** Confere o site e a porta e monta a forma ASCII (que o {@link URI} precisa aceitar). */
    private static Ascii asciiForm(String value) {
        int start = value.indexOf("://") + 3;
        int end = value.length();
        for (char c : new char[] {'/', '?', '#'}) {
            int index = value.indexOf(c, start);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String scheme = value.substring(0, start);
        String authority = value.substring(start, end);
        String rest = value.substring(end);
        if (authority.contains("@")) {
            return new Ascii(null, ERROR_USER_INFO);
        }

        String host;
        String port = "";
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                return new Ascii(null, ERROR_INVALID);
            }
            host = authority.substring(0, close + 1);
            port = authority.substring(close + 1);
        } else {
            int colon = authority.lastIndexOf(':');
            host = colon >= 0 ? authority.substring(0, colon) : authority;
            port = colon >= 0 ? authority.substring(colon) : "";
        }
        if (!port.isEmpty() && !validPort(port)) {
            return new Ascii(null, ERROR_INVALID);
        }
        if (host.isEmpty()) {
            return new Ascii(null, ERROR_NO_HOST);
        }

        String asciiHost = host;
        if (!host.startsWith("[")) {
            try {
                asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            } catch (IllegalArgumentException e) {
                return new Ascii(null, ERROR_INVALID);
            }
            if (!HOST_CHARS.matcher(asciiHost).matches() || asciiHost.startsWith(".")
                    || asciiHost.contains("..") || asciiHost.startsWith("-")) {
                return new Ascii(null, ERROR_INVALID);
            }
            String bare = asciiHost.endsWith(".") ? asciiHost.substring(0, asciiHost.length() - 1) : asciiHost;
            if (!bare.contains(".") && !bare.equalsIgnoreCase("localhost")) {
                return new Ascii(null, ERROR_NO_DOMAIN);
            }
        }

        try {
            URI uri = new URI(scheme + asciiHost + port + rest);
            if (uri.getHost() == null || uri.getRawUserInfo() != null) {
                return new Ascii(null, ERROR_INVALID);
            }
            return new Ascii(uri.toASCIIString(), null);
        } catch (URISyntaxException e) {
            return new Ascii(null, ERROR_INVALID);
        }
    }

    private static boolean validPort(String port) {
        if (port.length() < 2 || port.length() > 6 || port.charAt(0) != ':') {
            return false;
        }
        for (int i = 1; i < port.length(); i++) {
            if (!Character.isDigit(port.charAt(i)) || port.charAt(i) > '9') {
                return false;
            }
        }
        int value = Integer.parseInt(port.substring(1));
        return value >= 1 && value <= 65535;
    }

    private static String abbreviate(String text) {
        return text.length() <= 20 ? text : text.substring(0, 20) + "…";
    }
}
