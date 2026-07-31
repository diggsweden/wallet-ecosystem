import {
  d as defineEventHandler,
  r as readBody,
  u as useStorage,
  c as createError,
  a as useRuntimeConfig,
} from "../../nitro/nitro.mjs";
import { randomUUID } from "crypto";
import { c as createLogger } from "../../_/logger.mjs";
import "node:http";
import "node:https";
import "node:events";
import "node:buffer";
import "node:fs";
import "node:path";
import "node:crypto";
import "node:url";
import "consola";

const biocentralenRequest_post = defineEventHandler(async (event) => {
  const config = useRuntimeConfig();
  const hostApi =
    process.env.INTERNAL_HOST_API ||
    process.env.HOST_API ||
    config.public.hostApi ||
    "http://eudi-verifier-backend:8080";
  const publicBaseUrl =
    process.env.PUBLIC_BASE_URL ||
    process.env.NUXT_PUBLIC_BASE_URL ||
    "https://custom-verifier";
  const { flow_type } = await readBody(event);
  const presentationUrl = `${hostApi}/ui/presentations`;
  const logger = createLogger("biocentralen-request");
  try {
    const verifyId = randomUUID();
    const requestBody = {
      type: "vp_token",
      dcql_query: {
        credentials: [
          {
            id: "birthdate_credential",
            format: "dc+sd-jwt",
            vct: "urn:eudi:pid:1",
            meta: {
              doctype_value: "eu.europa.ec.eudi.pid.1",
            },
            claims: [
              {
                path: ["birthdate"],
              },
            ],
          },
          {
            id: "family_name_credential",
            format: "dc+sd-jwt",
            vct: "urn:eudi:pid:1",
            meta: {
              doctype_value: "eu.europa.ec.eudi.pid.1",
            },
            claims: [
              {
                path: ["family_name"],
              },
            ],
          },
        ],
        credential_sets: [
          {
            options: [["birthdate_credential"]],
            required: true,
          },
          {
            options: [["family_name_credential"]],
            required: false,
          },
        ],
      },
      nonce: randomUUID(),
      request_uri_method: "get",
      response_mode: "direct_post",
      intended_use_id: "1",
    };
    if (flow_type === "same_device") {
      requestBody.wallet_response_redirect_uri_template = `${publicBaseUrl}/api/verifier-status/biocentralen/${verifyId}?response_code={RESPONSE_CODE}`;
    }
    logger.info("A new presentation initiated");
    logger.info("Sending presentation request to verifier backend", {
      verifyId,
      flowType: flow_type,
      "url.full": presentationUrl,
    });
    logger.debug("Request body", requestBody);
    const response = await $fetch(presentationUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: requestBody,
      ignoreHTTPSErrors: true,
    });
    logger.info("Request successful", {
      verifyId,
      transactionId: response.transaction_id,
    });
    const storage = useStorage("memory");
    await storage.setItem(`verify:${verifyId}`, {
      transactionId: response.transaction_id,
    });
    return response;
  } catch (error) {
    const responseStatus =
      typeof error === "object" &&
      error !== null &&
      "response" in error &&
      typeof error.response === "object" &&
      error.response !== null &&
      "status" in error.response &&
      typeof error.response.status === "number"
        ? error.response.status
        : void 0;
    logger.error("Verifier request failed", {
      "exception.message":
        error instanceof Error ? error.message : String(error),
      "http.response.status_code": responseStatus,
      "url.full": presentationUrl,
      "response.body": error.data ? JSON.stringify(error.data) : "No data",
    });
    throw createError({
      statusCode: responseStatus,
      statusMessage: "Failed to create verification request",
    });
  }
});

export { biocentralenRequest_post as default };
//# sourceMappingURL=biocentralen-request.post.mjs.map
