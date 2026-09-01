import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";

export const options = {
    scenarios: {
        doctor_portal_load: {
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
        // Replace with your doctor account
        email: "doctor@demo.com",
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
        console.log(loginRes.body);
        throw new Error("Doctor login failed");
    }

    const body = JSON.parse(loginRes.body);

    return {
        token: body.data.token
    };
}

export default function (data) {

    // Change this to an existing doctorId
    const doctorId = 6;

    const res = http.get(
        `${BASE_URL}/doctor-portal/my-queue/${doctorId}`,
        {
            headers: {
                Authorization: `Bearer ${data.token}`
            }
        }
    );

    if (res.status !== 200) {
        console.log("Status:", res.status);
        console.log("Body:", res.body);
    }

    check(res, {
        "doctor queue success": (r) => r.status === 200
    });

    sleep(1);
}