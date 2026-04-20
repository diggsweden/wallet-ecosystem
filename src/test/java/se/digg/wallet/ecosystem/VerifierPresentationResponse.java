// SPDX-FileCopyrightText: 2025 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

@SuppressWarnings("checkstyle:RecordComponentName")
public record VerifierPresentationResponse(
    String transaction_id,
    String client_id,
    String request_uri,
    String request_uri_method,
    String request) {
}
