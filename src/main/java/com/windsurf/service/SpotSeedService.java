package com.windsurf.service;

import com.windsurf.model.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class SpotSeedService {

    void onStart(@Observes StartupEvent ev) {
        seedIfEmpty();
    }

    @Transactional
    void seedIfEmpty() {
        for (SpotEntity e : SEED_SPOTS) {
            if (SpotEntity.find("externalId", e.externalId).count() == 0) {
                e.persist();
            }
        }
    }

    private static final List<SpotEntity> SEED_SPOTS = List.of(
        spot("sandhamn", "Sandhamn", 59.27, 18.92, "Stockholms skärgård",
            SpotType.WAVES, DifficultyLevel.INTERMEDIATE, 9, 6, 15, "W,SW,NW",
            "Öppen Baltikexponering med konsekvent dyning. Bäst i sommarens västliga vindar.", null),

        spot("arholma", "Arholma", 59.85, 19.13, "Stockholms skärgård",
            SpotType.BUMP_N_JUMP, DifficultyLevel.ADVANCED, 8, 5, 14, "S,SW,SE",
            "Yttre skärgårdsö med gap-vindar och starka accelerationszoner.", null),

        spot("ekero", "Ekerö / Mälaren", 59.28, 17.83, "Mälaren",
            SpotType.FLAT_WATER, DifficultyLevel.BEGINNER, 7, 4, 11, "SW,W,S",
            "Stor skyddad sjö, perfekt för nybörjare och freestyle. Ingen ström.", "Parkering vid Ekerö strand. Inga hinder."),

        spot("landsort", "Landsort", 58.74, 17.86, "Södra Stockholmskusten",
            SpotType.WAVES, DifficultyLevel.ADVANCED, 10, 8, 18, "SW,W,NW",
            "Exponerad fyröplats. Lång fetch från söder ger riktig vågsegling.", "Endast båttransport. Planera tidigt."),

        spot("nynashamn", "Nynäshamn", 58.90, 17.95, "Södra Stockholmskusten",
            SpotType.FLAT_WATER, DifficultyLevel.INTERMEDIATE, 8, 5, 13, "W,NW,SW",
            "Öppen bukt med bra fetch. Håll koll på färjezoner.", "Parkering vid hamnplan. Bra tillgänglighet."),

        spot("oxelosund", "Oxelösund", 58.67, 17.10, "Södermanlands kust",
            SpotType.FLAT_WATER, DifficultyLevel.INTERMEDIATE, 7, 5, 12, "SW,S,W",
            "Industrihamn med förvånansvärt ren vind. Flat chop.", null),

        spot("oreground", "Öregrund", 60.34, 18.45, "Upplands skärgård",
            SpotType.BUMP_N_JUMP, DifficultyLevel.INTERMEDIATE, 8, 5, 14, "S,SE,SW",
            "Norra skärgården med konstant sydlig ström på sommaren.", null),

        spot("trosa", "Trosa", 58.90, 17.55, "Södermanlands kust",
            SpotType.FLAT_WATER, DifficultyLevel.BEGINNER, 6, 4, 10, "SW,W,NW",
            "Lugn, skyddad bukt. Bra för inlärning. Skyddad från stora dyningar.", "Gratis parkering vid stranden."),

        spot("vastervik", "Västervik", 57.76, 16.65, "Smålands kust",
            SpotType.WAVES, DifficultyLevel.INTERMEDIATE, 9, 6, 15, "W,SW,NW",
            "Södra Östersjökusten med öppen västlig fetch och klipplandskap.", null),

        spot("strangnas", "Strängnäs / Mälaren Väst", 59.38, 17.03, "Mälaren",
            SpotType.FLAT_WATER, DifficultyLevel.BEGINNER, 7, 4, 11, "SW,S,W",
            "Västra Mälarenbukt, mycket flat och skyddad. Bra inlärningsspot.", "Enkel tillgång, bra parkering."),

        spot("gotska-sandon", "Gotska Sandön", 58.38, 19.22, "Öppna Östersjön",
            SpotType.WAVES, DifficultyLevel.EXPERT, 11, 8, 21, "W,SW,NW,S",
            "Avlägsen obefolkad ö, full Östersjöfetch. Endast för experter.", "Planeras med färja från Nynäshamn eller Fårösund."),

        spot("gotland-slite", "Gotland / Slite", 57.71, 18.80, "Gotlands östkust",
            SpotType.BUMP_N_JUMP, DifficultyLevel.INTERMEDIATE, 8, 6, 14, "NW,N,W",
            "Östkust på Gotland. NV-vindar skapar kul tvärs-shore förhållanden.", null),

        spot("appelviken-varberg", "Appelviken, Varberg", 57.133, 12.273, "Hallandskusten",
            SpotType.FLAT_WATER, DifficultyLevel.INTERMEDIATE, 9, 5, 16, "W,SW,NW",
            "Klassisk Kattegattspot med bra fetch för västliga vindar. Flatvatten nära land, chop längre ut.",
            "Parkering vid Apelvikens camping. Grusväg ned till stranden."),

        spot("traslövslage-varberg", "Träslövsläge, Varberg", 57.042, 12.252, "Hallandskusten",
            SpotType.FLAT_WATER, DifficultyLevel.INTERMEDIATE, 9, 5, 16, "W,SW,S",
            "Bra V-SW spot söder om Varberg. Lång fetch från Kattegatt. Populär på sommaren.",
            "Parkering vid hamnen. Plan väg till stranden, inga hinder."),

        spot("skanor-falsterbo-gasthamn", "Skanör-Falsterbo, Gästhamnen", 55.413, 12.856, "Falsterbonäset",
            SpotType.BUMP_N_JUMP, DifficultyLevel.INTERMEDIATE, 10, 6, 18, "SW,W,NW",
            "Sydvästligaste spetsen av Sverige. Exponerad mot både Kattegatt och Öresund. Kraftiga vindaccelerationer runt näset.",
            "Parkering vid gästhamnen i Skanör. God tillgänglighet, sandstrand.")
    );

    private static SpotEntity spot(String extId, String name, double lat, double lon, String region,
                                    SpotType type, DifficultyLevel diff,
                                    double ideal, double min, double max,
                                    String dirs, String description, String accessInfo) {
        SpotEntity e = new SpotEntity();
        e.externalId = extId;
        e.name = name;
        e.latitude = lat;
        e.longitude = lon;
        e.region = region;
        e.type = type;
        e.difficulty = diff;
        e.idealWindSpeed = ideal;
        e.minWindSpeed = min;
        e.maxWindSpeed = max;
        e.bestDirections = dirs;
        e.description = description;
        e.accessInfo = accessInfo;
        e.source = SpotSource.SEED;
        e.approved = true;
        return e;
    }
}
