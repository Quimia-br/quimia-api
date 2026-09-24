import assert from "node:assert/strict";
import test from "node:test";
import { deployWithClient, main } from "./deploy.mjs";

const validEnv = {
  DISCLOUD_TOKEN: "test-token-do-not-print",
  DISCLOUD_APP_ID: "1234567890",
  TAG_NAME: "v1.2.3",
  DISCLOUD_ARCHIVE: "target/discloud/quimia-api.zip",
};

test("logs in and updates the selected app with the release archive", async () => {
  const calls = [];
  const client = {
    async login(token) {
      calls.push(["login", token]);
    },
    apps: {
      async update(appId, options) {
        calls.push(["update", appId, options]);
      },
    },
  };

  await deployWithClient(client, validEnv);

  assert.deepEqual(calls, [
    ["login", validEnv.DISCLOUD_TOKEN],
    ["update", validEnv.DISCLOUD_APP_ID, { file: validEnv.DISCLOUD_ARCHIVE }],
  ]);
});

for (const [name, overrides] of [
  ["token", { DISCLOUD_TOKEN: "" }],
]) {
  test(`rejects a missing ${name} before using the client`, async () => {
    let calls = 0;
    const client = {
      async login() {
        calls += 1;
      },
      apps: {
        async update() {
          calls += 1;
        },
      },
    };

    await assert.rejects(deployWithClient(client, { ...validEnv, ...overrides }));
    assert.equal(calls, 0);
  });
}

test("creates the first app when the Discloud account has no apps", async () => {
  const calls = [];
  const client = {
    async login(token) {
      calls.push(["login", token]);
    },
    user: {
      async fetch() {
        calls.push(["fetch-user"]);
        return { user: { apps: [] } };
      },
    },
    apps: {
      async create(options) {
        calls.push(["create", options]);
      },
    },
  };

  const result = await deployWithClient(client, { ...validEnv, DISCLOUD_APP_ID: "" });

  assert.equal(result, "created");
  assert.deepEqual(calls, [
    ["login", validEnv.DISCLOUD_TOKEN],
    ["fetch-user"],
    ["create", { file: validEnv.DISCLOUD_ARCHIVE }],
  ]);
});

test("refuses to create a duplicate when an app exists but its ID is not configured", async () => {
  let createCalls = 0;
  const client = {
    async login() {},
    user: {
      async fetch() {
        return { user: { apps: ["existing-app-id"] } };
      },
    },
    apps: {
      async create() {
        createCalls += 1;
      },
    },
  };

  await assert.rejects(
    deployWithClient(client, { ...validEnv, DISCLOUD_APP_ID: "" }),
    /DISCLOUD_APP_ID/,
  );
  assert.equal(createCalls, 0);
});

test("fails closed when the account response does not expose its app list", async () => {
  let createCalls = 0;
  const client = {
    async login() {},
    user: {
      async fetch() {
        return { user: {} };
      },
    },
    apps: {
      async create() {
        createCalls += 1;
      },
    },
  };

  await assert.rejects(deployWithClient(client, { ...validEnv, DISCLOUD_APP_ID: "" }));
  assert.equal(createCalls, 0);
});

test("rejects a non-release tag before using the client", async () => {
  let calls = 0;
  const client = {
    async login() {
      calls += 1;
    },
    apps: {
      async update() {
        calls += 1;
      },
    },
  };

  await assert.rejects(deployWithClient(client, { ...validEnv, TAG_NAME: "main" }));
  assert.equal(calls, 0);
});

test("main reports only a fixed message when the SDK rejects an upload", async () => {
  const stderr = [];
  const sdkError = new Error(`request failed for ${validEnv.DISCLOUD_TOKEN}`);
  const code = await main(
    validEnv,
    async () => ({
      async login() {},
      apps: {
        async update() {
          throw sdkError;
        },
      },
    }),
    (message) => stderr.push(message),
  );

  assert.equal(code, 1);
  assert.deepEqual(stderr, ["Discloud upload failed"]);
  assert.equal(stderr.join("\n").includes(validEnv.DISCLOUD_TOKEN), false);
});

test("main validates the tag before constructing the SDK client", async () => {
  let clientsCreated = 0;
  const stderr = [];
  const code = await main(
    { ...validEnv, TAG_NAME: "main" },
    async () => {
      clientsCreated += 1;
      return {};
    },
    (message) => stderr.push(message),
  );

  assert.equal(code, 1);
  assert.equal(clientsCreated, 0);
  assert.deepEqual(stderr, ["Discloud upload failed"]);
});
