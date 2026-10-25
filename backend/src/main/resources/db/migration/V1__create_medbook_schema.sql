








CREATE TABLE roles (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0,
    last_login_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CHECK (phone_number REGEXP '^\\+?[0-9\\s\\-]+$'),
    INDEX idx_users_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE specialties (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE doctors (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL UNIQUE,
    specialty_id BIGINT UNSIGNED NOT NULL,
    medical_license_number VARCHAR(50) NOT NULL UNIQUE,
    years_experience TINYINT UNSIGNED NOT NULL,
    bio TEXT,
    consultation_fee DECIMAL(10,2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    profile_picture_url VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    FOREIGN KEY (specialty_id) REFERENCES specialties(id) ON DELETE RESTRICT,
    INDEX idx_doctors_specialty (specialty_id),

    CHECK (consultation_fee >= 0),
    CHECK (years_experience BETWEEN 0 AND 80)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE patients (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL UNIQUE,
    date_of_birth DATE NOT NULL,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(50),
    postal_code VARCHAR(20),
    country VARCHAR(50),
    emergency_contact_name VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    has_insurance BOOLEAN NOT NULL DEFAULT FALSE,
    insurance_provider VARCHAR(100),
    insurance_policy_number VARCHAR(50),
    allergies TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,

    CHECK (has_insurance = FALSE OR (insurance_provider IS NOT NULL AND insurance_policy_number IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE doctor_availability (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT UNSIGNED NOT NULL,
    day_of_week TINYINT UNSIGNED NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_recurring BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE,
    effective_until DATE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE,

    UNIQUE KEY uk_availability_doctor_day_time (doctor_id, day_of_week, start_time, end_time),

    CHECK (day_of_week BETWEEN 0 AND 6),
    CHECK (start_time < end_time),
    CHECK (effective_from <= effective_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE appointments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT UNSIGNED NOT NULL,
    doctor_id BIGINT UNSIGNED NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    reason VARCHAR(500),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    cancellation_reason VARCHAR(255),
    consultation_fee_snapshot DECIMAL(10,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    confirmed_at DATETIME(3),
    completed_at DATETIME(3),

    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT,

    INDEX idx_appointments_doctor_date (doctor_id, appointment_date),
    INDEX idx_appointments_patient_date (patient_id, appointment_date),
    INDEX idx_appointments_status (status),

    CHECK (start_time < end_time),
    CHECK (status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    CHECK (status != 'CANCELLED' OR cancellation_reason IS NOT NULL),
    CHECK (consultation_fee_snapshot >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;





CREATE TABLE appointment_status_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED,
    notes VARCHAR(500),
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT,
    FOREIGN KEY (changed_by_user_id) REFERENCES users(id) ON DELETE SET NULL,

    INDEX idx_status_history_appointment (appointment_id),
    INDEX idx_status_history_created (changed_at),

    CHECK (status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;




INSERT INTO roles (id, name, description) VALUES
    (1, 'ADMIN', 'Administrator with full system access'),
    (2, 'DOCTOR', 'Healthcare provider'),
    (3, 'PATIENT', 'Patient seeking medical services');
