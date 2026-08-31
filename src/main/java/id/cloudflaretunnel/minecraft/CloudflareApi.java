package id.cloudflaretunnel.minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Minimal client for the two Cloudflare API operations required by provisioning. */
final class CloudflareApi {
    private static final URI API_ROOT = URI.create("https://api.cloudflare.com/client/v4/");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private CloudflareApi() {
    }

    static void provisionTcpRoute(TunnelSettings.CloudflareApiSettings settings) throws ApiException {
        String configuration = get(settings, "accounts/" + settings.accountId()
                + "/cfd_tunnel/" + settings.tunnelId() + "/configurations");
        String result = requiredObject(configuration, "result", "Konfigurasi tunnel tidak ditemukan pada respons API.");
        String config = requiredObject(result, "config", "Tunnel belum memiliki konfigurasi yang dapat diperbarui.");
        String updatedConfig = withTcpIngress(config, settings.hostname(), settings.serviceUrl());

        put(settings, "accounts/" + settings.accountId() + "/cfd_tunnel/" + settings.tunnelId()
                + "/configurations", "{\"config\":" + updatedConfig + "}");

        upsertTunnelDns(settings);
    }

    private static void upsertTunnelDns(TunnelSettings.CloudflareApiSettings settings) throws ApiException {
        String query = "zones/" + settings.zoneId() + "/dns_records?name="
                + URLEncoder.encode(settings.hostname(), StandardCharsets.UTF_8);
        String records = get(settings, query);
        String result = requiredArray(records, "result", "Daftar DNS tidak ditemukan pada respons API.");
        List<String> entries = splitTopLevel(result);
        String cnameId = null;

        for (String entry : entries) {
            String type = stringField(entry, "type");
            if ("CNAME".equals(type)) {
                cnameId = stringField(entry, "id");
            } else {
                throw new ApiException("Hostname " + settings.hostname() + " sudah memiliki record " + type
                        + ". Hapus atau pindahkan record tersebut terlebih dahulu; plugin tidak akan menimpanya.");
            }
        }

        String dnsPayload = "{\"type\":\"CNAME\",\"name\":\"" + json(settings.hostname())
                + "\",\"content\":\"" + json(settings.tunnelDnsTarget())
                + "\",\"proxied\":true,\"ttl\":1}";
        if (cnameId == null || cnameId.isBlank()) {
            post(settings, "zones/" + settings.zoneId() + "/dns_records", dnsPayload);
        } else {
            patch(settings, "zones/" + settings.zoneId() + "/dns_records/" + cnameId, dnsPayload);
        }
    }

    private static String withTcpIngress(String config, String hostname, String serviceUrl) throws ApiException {
        int ingressStart = valueStart(config, "ingress");
        String newRule = "{\"hostname\":\"" + json(hostname) + "\",\"service\":\"" + json(serviceUrl) + "\"}";
        if (ingressStart < 0) {
            int closingBrace = config.lastIndexOf('}');
            if (closingBrace < 0) {
                throw new ApiException("Format konfigurasi tunnel dari Cloudflare tidak valid.");
            }
            String prefix = config.substring(0, closingBrace).trim();
            String separator = prefix.endsWith("{") ? "" : ",";
            return prefix + separator + "\"ingress\":[" + newRule + ",{\"service\":\"http_status:404\"}]}";
        }

        int ingressEnd = balancedEnd(config, ingressStart);
        if (config.charAt(ingressStart) != '[' || ingressEnd < 0) {
            throw new ApiException("Ingress tunnel dari Cloudflare tidak berbentuk daftar JSON.");
        }

        List<String> rules = new ArrayList<>();
        boolean hasCatchAll = false;
        for (String rule : splitTopLevel(config.substring(ingressStart, ingressEnd + 1))) {
            if (hostname.equalsIgnoreCase(stringField(rule, "hostname"))) {
                continue;
            }
            if (stringField(rule, "hostname") == null) {
                hasCatchAll = true;
            }
            rules.add(rule);
        }
        rules.add(newRule);
        if (!hasCatchAll) {
            rules.add("{\"service\":\"http_status:404\"}");
        }
        return config.substring(0, ingressStart) + "[" + String.join(",", rules) + "]"
                + config.substring(ingressEnd + 1);
    }

    private static String get(TunnelSettings.CloudflareApiSettings settings, String path) throws ApiException {
        return request(settings, "GET", path, null);
    }

    private static void post(TunnelSettings.CloudflareApiSettings settings, String path, String body) throws ApiException {
        request(settings, "POST", path, body);
    }

    private static void put(TunnelSettings.CloudflareApiSettings settings, String path, String body) throws ApiException {
        request(settings, "PUT", path, body);
    }

    private static void patch(TunnelSettings.CloudflareApiSettings settings, String path, String body) throws ApiException {
        request(settings, "PATCH", path, body);
    }

    private static String request(TunnelSettings.CloudflareApiSettings settings, String method, String path, String body)
            throws ApiException {
        HttpRequest.Builder request = HttpRequest.newBuilder(API_ROOT.resolve(path))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + settings.apiToken())
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2 || !response.body().contains("\"success\":true")) {
                throw new ApiException("HTTP " + response.statusCode() + ": " + apiMessage(response.body()));
            }
            return response.body();
        } catch (IOException exception) {
            throw new ApiException("Tidak dapat terhubung ke Cloudflare API: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException("Permintaan ke Cloudflare API terinterupsi.");
        }
    }

    private static String apiMessage(String response) {
        String message = stringField(response, "message");
        return message == null ? "Respons Cloudflare tidak berhasil." : message;
    }

    private static String requiredObject(String json, String field, String message) throws ApiException {
        int start = valueStart(json, field);
        if (start < 0 || json.charAt(start) != '{') {
            throw new ApiException(message);
        }
        int end = balancedEnd(json, start);
        if (end < 0) {
            throw new ApiException(message);
        }
        return json.substring(start, end + 1);
    }

    private static String requiredArray(String json, String field, String message) throws ApiException {
        int start = valueStart(json, field);
        if (start < 0 || json.charAt(start) != '[') {
            throw new ApiException(message);
        }
        int end = balancedEnd(json, start);
        if (end < 0) {
            throw new ApiException(message);
        }
        return json.substring(start, end + 1);
    }

    private static int valueStart(String json, String field) {
        String quotedField = "\"" + field + "\"";
        int fieldStart = json.indexOf(quotedField);
        if (fieldStart < 0) {
            return -1;
        }
        int colon = json.indexOf(':', fieldStart + quotedField.length());
        if (colon < 0) {
            return -1;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        return start;
    }

    private static int balancedEnd(String json, int start) {
        char open = json.charAt(start);
        char close = open == '{' ? '}' : open == '[' ? ']' : 0;
        if (close == 0) {
            return -1;
        }
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == open) {
                depth++;
            } else if (current == close && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String array) throws ApiException {
        if (array.length() < 2 || array.charAt(0) != '[' || array.charAt(array.length() - 1) != ']') {
            throw new ApiException("Format daftar JSON dari Cloudflare tidak valid.");
        }
        List<String> values = new ArrayList<>();
        int valueStart = 1;
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 1; index < array.length() - 1; index++) {
            char current = array.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                objectDepth++;
            } else if (current == '}') {
                objectDepth--;
            } else if (current == '[') {
                arrayDepth++;
            } else if (current == ']') {
                arrayDepth--;
            } else if (current == ',' && objectDepth == 0 && arrayDepth == 0) {
                addValue(values, array.substring(valueStart, index));
                valueStart = index + 1;
            }
        }
        addValue(values, array.substring(valueStart, array.length() - 1));
        return values;
    }

    private static void addValue(List<String> values, String value) {
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            values.add(trimmed);
        }
    }

    private static String stringField(String json, String field) {
        int start = valueStart(json, field);
        if (start < 0 || json.charAt(start) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return value.toString();
            } else {
                value.append(current);
            }
        }
        return null;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }
}
