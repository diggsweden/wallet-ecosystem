// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

public class Healthcheck {
    public static void main(String[] args) throws Exception {
        var uri = URI.create(args[0]);
        var response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(uri).GET().build(),
                        BodyHandlers.discarding());

        if (response.statusCode() == 200) {
            System.exit(0);
        } else {
            throw new RuntimeException(response.toString());
        }
    }
}
