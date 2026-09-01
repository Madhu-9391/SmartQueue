import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";

export const options = {
    scenarios: {
        queue_status_load: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
    { duration: "30s", target: 100 },
    { duration: "30s", target: 250 },
    { duration: "30s", target: 500 },
    { duration: "30s", target: 750 },
    { duration: "30s", target: 1000 },
    { duration: "30s", target: 0 },
],
            gracefulRampDown: "30s"
        }
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<200"]
    }
};

export function setup() {

    const loginPayload = JSON.stringify({
        email: "patient@demo.com",
        password: "password"
    });

    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        loginPayload,
        {
            headers: {
                "Content-Type": "application/json"
            }
        }
    );

    check(loginRes, {
        "login success": (r) => r.status === 200
    });

    if (loginRes.status !== 200) {
        console.log("Login Status:", loginRes.status);
        console.log("Login Body:", loginRes.body);
        throw new Error("Login failed");
    }

    const body = JSON.parse(loginRes.body);

    return {
        token: body.data.token
    };
}

export default function (data) {

    // Change queueId if required
    const queueId = 1;

    const res = http.get(
        `${BASE_URL}/queue/status/${queueId}`,
        {
            headers: {
                Authorization: `Bearer ${data.token}`,
                "Content-Type": "application/json"
            }
        }
    );

    if (res.status !== 200) {
        console.log("Status:", res.status);
        console.log("Body:", res.body);
    }

    check(res, {
        "queue status success": (r) => r.status === 200
    });

    sleep(1);
}