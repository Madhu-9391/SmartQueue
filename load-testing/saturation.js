import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";

export const options = {
    scenarios: {
        saturation_test: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
    { duration: "30s", target: 500 },
    { duration: "30s", target: 750 },
    { duration: "30s", target: 1000 },
    { duration: "30s", target: 0 },
],
            gracefulRampDown: "30s",
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

function login(email, password) {
    const res = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email,
            password,
        }),
        {
            headers: {
                "Content-Type": "application/json",
            },
        }
    );

    if (res.status !== 200) {
        console.log(`Login failed: ${email}`);
        console.log(res.body);
        return null;
    }

    check(res, {
        "login success": (r) => r.status === 200,
    });

    return res.json("data.token");
}

export function setup() {
    const patientToken = login(
        "patient@demo.com",
        "password"
    );

    const doctorToken = login(
        "doctor@demo.com",
        "password"
    );

    if (!patientToken || !doctorToken) {
        throw new Error("Setup failed: authentication failed");
    }

    console.log("Both test users authenticated successfully");

    return {
        patientToken,
        doctorToken,
    };
}

export default function (data) {

    // Queue Status
    const queue = http.get(
        `${BASE_URL}/queue/status/1`,
        {
            headers: {
                Authorization: `Bearer ${data.patientToken}`,
            },
        }
    );

    check(queue, {
        "queue success": (r) => r.status === 200,
    });

    // Doctor Portal
    const doctor = http.get(
        `${BASE_URL}/doctor-portal/my-queue/6`,
        {
            headers: {
                Authorization: `Bearer ${data.doctorToken}`,
            },
        }
    );

    check(doctor, {
        "doctor success": (r) => r.status === 200,
    });

    // Analytics
    const analytics = http.get(
        `${BASE_URL}/analytics/dashboard`,
        {
            headers: {
                Authorization: `Bearer ${data.patientToken}`,
            },
        }
    );

    check(analytics, {
        "analytics success": (r) => r.status === 200,
    });

    sleep(1);
}