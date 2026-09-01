# SmartQueue v3 — Intelligent Hospital Queue Management System

Full-stack enterprise hospital queue system: Spring Boot + React + Python ML microservice.

---

### Payment, email receipt and time handling
- Payment is mandatory. An appointment remains `PAYMENT_PENDING` until the backend verifies the Razorpay signature.
- The appointment enters the live queue only after successful server-side verification.
- A payment receipt email is sent asynchronously after verification when SMTP is configured.
- Application business time is explicitly `Asia/Kolkata` (IST) across the JVM, MySQL connection and UI display.
- Notification read state is persisted in the database and supports both per-notification and mark-all read operations.

## Features

| Feature | Description |
|---|---|
| 🤖 AI ETA Prediction | Hybrid heuristic/ML-ready prediction engine estimates consultation time with ±N min confidence |
| 📋 Live Queue | Priority-sorted queue (EMERGENCY→VIP→SENIOR→NORMAL) with WebSocket real-time updates |
| 👨‍⚕️ Doctor Portal | Doctors see their own queue, mark done, mark no-show, update availability |
| 🏥 Admin Panel | Full CRUD: doctors, users, queues, appointments. Role management, delay updates |
| 📊 Historical Analytics | 7/14/30-day trends, weekday distribution, doctor performance table |
| 🔍 Audit Log | Every priority escalation logged with who changed it, when, and why |
| 💳 Payment Gateway | Razorpay integration with constant-time HMAC-SHA256 signature verification |
| 📱 SMS + Email | Twilio SMS + Spring Mail notifications (disabled by default, enable via properties) |
| 📅 Reschedule | Patients can reschedule up to 2 times, carries payment status |
| ❌ Cancel + Reason | Structured cancellation reasons feed into analytics |
| ⚠️ Capacity Warnings | Auto-notifies admins when queue reaches 80% capacity |
| 🖥 Kiosk Mode | `/kiosk` — public tablet mode for walk-in patient self-registration |
| 🔄 Rate Limiting | Caffeine cache + configurable rate limits for booking endpoint |

---

## Quick Start

```bash
# 1. Backend (Java 17+, Maven)
cd backend
mvn spring-boot:run

# Starts on http://localhost:8080
# Swagger UI:  http://localhost:8080/swagger-ui.html

# 2. Frontend (Node 18+)
cd frontend
npm install && npm run dev
# → http://localhost:3000

# 3. Python ML Service (optional)
cd ml-service
pip install -r requirements.txt
uvicorn main:app --port 8000
# Then set: app.ml.service.enabled=true in application.properties
```

---

## Demo Accounts

| Role    | Email                | Password |
|---------|----------------------|----------|
| Admin   | admin@demo.com       | password |
| Patient | patient@demo.com     | password |
| Patient | priya@demo.com       | password |
| Patient | arjun@demo.com       | password |
| Doctor  | doctor.priya@demo.com | password |
| Doctor  | doctor.arun@demo.com  | password |
| Doctor  | doctor.sunita@demo.com | password |
| Doctor  | doctor.kiran@demo.com | password |

---

## Navigation by Role

### PATIENT
- `/dashboard` — Live queue view + charts
- `/book` — Book appointment with AI ETA prediction + Razorpay payment
- `/my-queue` — Active appointments, cancel with reason, reschedule

### DOCTOR
- `/dashboard` — System overview
- `/doctor-portal` — Own queue, mark done/no-show, availability control, day stats
- `/ai-engine` — AI model details

### ADMIN
- `/dashboard` — Full analytics
- `/admin` — 5-tab control panel (Queue, Doctors, Users, Queues, Appointments)
- `/doctor-portal` — Manage any doctor's queue
- `/historical` — Historical trends + audit log
- `/ai-engine` — AI engine + simulator
- `/kiosk` (new tab) — Reception tablet for walk-in registration

---

## API Reference

### Auth
```
POST /api/auth/register
POST /api/auth/login
```

### Appointments
```
POST   /api/appointments/book
GET    /api/appointments/my
DELETE /api/appointments/{id}          ← body: { reason }
POST   /api/appointments/{id}/reschedule
```

### Queue
```
GET    /api/queue/status/{queueId}
PUT    /api/queue/{queueId}/next
POST   /api/queue/create
```

### Payments
```
POST   /api/payments/create-order
POST   /api/payments/verify
GET    /api/payments/appointment/{id}
```

### Doctor Portal
```
GET    /api/doctor-portal/my-queue/{doctorId}
GET    /api/doctor-portal/stats/{doctorId}
PUT    /api/doctor-portal/{docId}/next
PUT    /api/doctor-portal/{docId}/appointments/{apptId}/done
PUT    /api/doctor-portal/{docId}/appointments/{apptId}/no-show
PUT    /api/doctor-portal/{docId}/availability?status=AVAILABLE
```

### Admin (ADMIN role required)
```
GET/POST/PUT/DELETE  /api/admin/doctors/**
GET/PUT/DELETE       /api/admin/users/**
GET/PUT/DELETE       /api/admin/queues/**
GET/PUT              /api/admin/appointments/**

POST                 /api/admin/notify/broadcast
GET                  /api/admin/audit/priority?days=7
GET                  /api/admin/audit/historical?days=7
```

---

## Payment Integration (Razorpay)

```properties
# application.properties / environment
razorpay.key.id=rzp_test_YOUR_KEY_ID
razorpay.key.secret=YOUR_KEY_SECRET
razorpay.consultation.fee=200.00
app.payment.required=true    # payment is mandatory before queue confirmation
```

Flow:
1. Patient books → `POST /api/payments/create-order` → gets `razorpayOrderId`
2. Frontend opens Razorpay checkout with the order ID
3. On success → `POST /api/payments/verify` with signature
4. Backend verifies HMAC-SHA256 → activates appointment → WebSocket push

---

## Notifications

```properties
# SMS via Twilio
twilio.enabled=true
twilio.account.sid=AC...
twilio.auth.token=...
twilio.phone.number=+1...

# Email via Gmail
spring.mail.enabled=true
spring.mail.username=you@gmail.com
spring.mail.password=app-password
```

Events that trigger notifications:
- Token called → SMS + Email + in-app
- ETA updated → in-app
- Doctor delayed → SMS + Email + in-app
- Appointment cancelled → SMS + Email + in-app
- Payment confirmed → in-app
- Queue at 80% capacity → admin in-app

---

## Docker

```bash
docker-compose up --build
# Frontend:   http://localhost:3000
# Backend:    http://localhost:8080
# ML Service: internal Docker network only
# MySQL:      localhost:3306
# Set MYSQL_ROOT_PASSWORD, SPRING_DATASOURCE_PASSWORD and APP_JWT_SECRET before deployment.
```

## Payment Flow

Payment is mandatory for appointment confirmation. Booking creates only a `PAYMENT_PENDING` reservation. No appointment-booked notification, live-queue WebSocket update, or queue confirmation is emitted until Razorpay payment verification succeeds.
