package com.placify.common.demo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fixed demo records shared by every service's demo seeder (enabled with
 * DEMO_DATA=true). Fixed ids let each service seed its own database
 * independently while still referring to the same users.
 */
public final class DemoData {

    private DemoData() {
    }

    public static final String PROPERTY = "placify.demo.enabled";

    /** Graduating batch of the demo students and the batch every demo drive is open to. */
    public static final int BATCH = 2027;

    public static final String STUDENT_PASSWORD = "Student@123";
    public static final String COMPANY_PASSWORD = "Company@123";
    public static final String TPO_PASSWORD = "Tpo@12345";

    public record Student(String id, String regNo, String name, String email, String branch, int batch, BigDecimal cgpa,
                          int backlogs, String phone, List<String> skills) {
    }

    public record Company(String id, String name, String email, String industry, String website, String about) {
    }

    public static final String TPO_ID = "00000000-0000-4000-8000-000000000301";
    public static final String TPO_EMPLOYEE_ID = "TPO1001";

    public static final List<Student> STUDENTS = List.of(
            new Student("00000000-0000-4000-8000-000000000101", "22BCE1001", "Aarav Sharma", "aarav.demo@placify.local", "CSE", BATCH,
                    new BigDecimal("8.92"), 0, "9000000001", List.of("Java", "Spring Boot", "MySQL", "Data Structures", "Docker")),
            new Student("00000000-0000-4000-8000-000000000102", "22BCE1002", "Diya Patel", "diya.demo@placify.local", "CSE", BATCH,
                    new BigDecimal("9.31"), 0, "9000000002", List.of("Python", "Machine Learning", "SQL", "React")),
            new Student("00000000-0000-4000-8000-000000000103", "22BIT1003", "Rohan Verma", "rohan.demo@placify.local", "IT", BATCH,
                    new BigDecimal("7.45"), 1, "9000000003", List.of("JavaScript", "Node.js", "MongoDB")),
            new Student("00000000-0000-4000-8000-000000000104", "22BEC1004", "Ananya Iyer", "ananya.demo@placify.local", "ECE", BATCH,
                    new BigDecimal("8.10"), 0, "9000000004", List.of("C", "Embedded Systems", "Python", "MATLAB")),
            new Student("00000000-0000-4000-8000-000000000105", "22BCE1005", "Kabir Singh", "kabir.demo@placify.local", "CSE", BATCH,
                    new BigDecimal("6.85"), 2, "9000000005", List.of("C++", "HTML", "CSS")),
            new Student("00000000-0000-4000-8000-000000000106", "22BIT1006", "Meera Nair", "meera.demo@placify.local", "IT", BATCH,
                    new BigDecimal("8.64"), 0, "9000000006", List.of("Java", "Android", "Kotlin", "Firebase", "SQL")));

    public static final List<Company> COMPANIES = List.of(
            new Company("00000000-0000-4000-8000-000000000201", "TechNova Solutions", "hr@technova.demo", "IT Services",
                    "https://technova.example.com", "TechNova builds cloud-native enterprise software for banks and government clients."),
            new Company("00000000-0000-4000-8000-000000000202", "FinEdge Analytics", "careers@finedge.demo", "FinTech",
                    "https://finedge.example.com", "FinEdge Analytics turns payment data into real-time risk and fraud insights."),
            new Company("00000000-0000-4000-8000-000000000203", "CloudPeak Systems", "campus@cloudpeak.demo", "Cloud infrastructure",
                    "https://cloudpeak.example.com", "CloudPeak runs a managed Kubernetes and serverless platform used by 4,000+ product teams."),
            new Company("00000000-0000-4000-8000-000000000204", "Quantix Labs", "talent@quantix.demo", "Artificial intelligence",
                    "https://quantix.example.com", "Quantix Labs builds language and vision models for document automation in insurance and banking."),
            new Company("00000000-0000-4000-8000-000000000205", "NexaRetail Tech", "hiring@nexaretail.demo", "E-commerce",
                    "https://nexaretail.example.com", "NexaRetail powers online stores for 2,500 brands with search, payments and same-day logistics."),
            new Company("00000000-0000-4000-8000-000000000206", "Voltrix Automotive", "careers@voltrix.demo", "Electric vehicles",
                    "https://voltrix.example.com", "Voltrix designs battery-management systems and motor controllers for electric two- and three-wheelers."),
            new Company("00000000-0000-4000-8000-000000000207", "MedSphere Health", "jobs@medsphere.demo", "Health technology",
                    "https://medsphere.example.com", "MedSphere connects 900 hospitals and clinics with its patient-records and tele-consultation platform."),
            new Company("00000000-0000-4000-8000-000000000208", "ByteForge Games", "studio@byteforge.demo", "Gaming",
                    "https://byteforge.example.com", "ByteForge is an independent studio making multiplayer mobile games played by 12 million people."),
            new Company("00000000-0000-4000-8000-000000000209", "SecureLayer Cyber", "recruit@securelayer.demo", "Cyber security",
                    "https://securelayer.example.com", "SecureLayer runs a 24x7 security operations centre and threat-intelligence platform for enterprises."),
            new Company("00000000-0000-4000-8000-000000000210", "Orbit Consulting Group", "freshers@orbit.demo", "IT services and consulting",
                    "https://orbitconsulting.example.com", "Orbit is a global IT services company with 60,000 engineers delivering projects for clients in 30 countries."),
            new Company("00000000-0000-4000-8000-000000000211", "GreenGrid Energy", "people@greengrid.demo", "Clean energy",
                    "https://greengrid.example.com", "GreenGrid forecasts solar and wind generation and optimises power trading for utilities."),
            new Company("00000000-0000-4000-8000-000000000212", "PixelCraft Studios", "work@pixelcraft.demo", "Product design and web",
                    "https://pixelcraft.example.com", "PixelCraft designs and builds web and mobile products for start-ups and media companies."));
}
