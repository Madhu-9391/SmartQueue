import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
    scenarios: {
        login_load: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "30s", target: 20 },
                { duration: "1m", target: 100 },
                { duration: "30s", target: 0 }
            ]
        }
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500"]
    }
};

export default function () {

    const payload = JSON.stringify({
        email: "patient@demo.com",
        password: "password"
    });

    const params = {
        headers: {
            "Content-Type": "application/json"
        }
    };

    const res = http.post(
    "http://localhost:8080/api/auth/login",
    payload,
    params
);

if (res.status !== 200) {
    console.log("Status:", res.status);
    console.log("Body:", res.body);
}

check(res, {
    "login success": (r) => r.status === 200
});

sleep(1);
}