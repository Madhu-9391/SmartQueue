import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api";

export const options = {
    scenarios: {
        mixed_load: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "30s", target: 20 },
                { duration: "1m", target: 100 },
                { duration: "30s", target: 0 }
            ],
            gracefulRampDown: "30s"
        }
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500"]
    }
};

// ----------------------------
// Login as Patient
// ----------------------------
function patientLogin() {

    const res = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email: "patient@demo.com",
            password: "password"
        }),
        {
            headers: {
                "Content-Type": "application/json"
            }
        }
    );

    check(res, {
        "patient login": (r) => r.status === 200
    });

    return JSON.parse(res.body).data.token;
}

// ----------------------------
// Login as Doctor
// ----------------------------
function doctorLogin() {

    const res = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({
            email: "doctor@demo.com",
            password: "password"
        }),
        {
            headers: {
                "Content-Type": "application/json"
            }
        }
    );

    check(res, {
        "doctor login": (r) => r.status === 200
    });

    return JSON.parse(res.body).data.token;
}

export function setup() {

    return {
        patientToken: patientLogin(),
        doctorToken: doctorLogin()
    };
}

export default function (data) {

    const r = Math.random();

    // ----------------------------
    // 35% Queue Status
    // ----------------------------
    if (r < 0.35) {

        const res = http.get(
            `${BASE_URL}/queue/status/1`,
            {
                headers: {
                    Authorization: `Bearer ${data.patientToken}`
                }
            }
        );

        check(res, {
            "queue": (r) => r.status === 200
        });
    }

    // ----------------------------
    // 25% Appointment Booking
    // ----------------------------
    else if (r < 0.60) {

        const res = http.post(
            `${BASE_URL}/appointments/book`,
            JSON.stringify({
                doctorId: 1,
                queueId: 1,
                priority: "NORMAL"
            }),
            {
                headers: {
                    Authorization: `Bearer ${data.patientToken}`,
                    "Content-Type": "application/json"
                }
            }
        );

        check(res, {
            "appointment": (r) => r.status === 200
        });
    }

    // ----------------------------
    // 15% Analytics Dashboard
    // ----------------------------
    else if (r < 0.75) {

        const res = http.get(
            `${BASE_URL}/analytics/dashboard`,
            {
                headers: {
                    Authorization: `Bearer ${data.patientToken}`
                }
            }
        );

        check(res, {
            "analytics": (r) => r.status === 200
        });
    }

    // ----------------------------
    // 15% Doctor Portal
    // ----------------------------
    else if (r < 0.90) {

        const res = http.get(
            `${BASE_URL}/doctor-portal/my-queue/6`,
            {
                headers: {
                    Authorization: `Bearer ${data.doctorToken}`
                }
            }
        );

        check(res, {
            "doctor": (r) => r.status === 200
        });
    }

    // ----------------------------
    // 10% Login
    // ----------------------------
    else {

        const res = http.post(
            `${BASE_URL}/auth/login`,
            JSON.stringify({
                email: "patient@demo.com",
                password: "password"
            }),
            {
                headers: {
                    "Content-Type": "application/json"
                }
            }
        );

        check(res, {
            "login": (r) => r.status === 200
        });
    }

    sleep(1);
}