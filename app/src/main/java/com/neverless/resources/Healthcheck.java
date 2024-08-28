package com.neverless.resources;

import io.javalin.http.Context;

public class Healthcheck {
    public void check(Context ctx) {
        ctx.status(200);
        ctx.result("OK");
    }
}
