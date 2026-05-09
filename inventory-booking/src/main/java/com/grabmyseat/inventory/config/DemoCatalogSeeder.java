package com.grabmyseat.inventory.config;

import com.grabmyseat.inventory.client.AuthServiceClient;
import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventLayoutRequest;
import com.grabmyseat.inventory.dto.CreateZoneRequest;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.service.EventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@Profile("demo")
public class DemoCatalogSeeder {

    @Bean
    ApplicationRunner seedDemoCatalog(EventRepository events, EventService eventService,
                                      AuthServiceClient authServiceClient,
                                      @Value("${demo.organizer-username:north-star-live}") String organizerUsername) {
        return args -> {
            // The organizer account is seeded independently by auth-service's own demo
            // seeder (a separate database, separate ID sequence). Resolving the ID by
            // username here - rather than guessing a fixed value - is what keeps these
            // catalog events actually owned by the account that can manage them.
            AuthServiceClient.LookupResult organizer = authServiceClient.lookupByUsername(organizerUsername);
            if (organizer == null) {
                throw new IllegalStateException("demo organizer account not found: " + organizerUsername);
            }
            Long organizerId = organizer.userId();
            Set<String> existingNames = new HashSet<>(events.findAll().stream().map(event -> event.getName()).toList());
            Instant now = Instant.now();
            if (!existingNames.contains("Mumbai Monsoon Live")) {
                seed(eventService, organizerId, request(
                        "Mumbai Monsoon Live",
                        "Mahalaxmi Racecourse, Mumbai",
                        "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1600&q=85",
                        now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS),
                        now.plus(1, ChronoUnit.MINUTES), null, SaleType.STANDARD,
                        List.of(zone("Lawn access", 240, "899"), zone("Premium deck", 80, "1599"))));
            }
            if (!existingNames.contains("Bengaluru Afterdark Sessions")) {
                seed(eventService, organizerId, request(
                        "Bengaluru Afterdark Sessions",
                        "Phoenix Marketcity, Bengaluru",
                        "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=1600&q=85",
                        now.plus(45, ChronoUnit.DAYS), now.plus(45, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS),
                        now.plus(1, ChronoUnit.MINUTES), now.plus(7, ChronoUnit.DAYS), SaleType.FLASH,
                        List.of(zone("General admission", 320, "699"), zone("Fast lane", 60, "1299"))));
            }
            if (!existingNames.contains("Delhi Stories: A Theatre Weekend")) {
                seed(eventService, organizerId, request(
                        "Delhi Stories: A Theatre Weekend",
                        "Kamani Auditorium, New Delhi",
                        "https://images.unsplash.com/photo-1503095396549-807759245b35?auto=format&fit=crop&w=1600&q=85",
                        now.plus(60, ChronoUnit.DAYS), now.plus(60, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                        now.plus(1, ChronoUnit.MINUTES), null, SaleType.STANDARD,
                        List.of(zone("Balcony", 180, "499"), zone("Orchestra", 120, "999"))));
            }
            if (!existingNames.contains("Kolkata Monsoon Sessions")) {
                seed(eventService, organizerId, request(
                        "Kolkata Monsoon Sessions",
                        "Rabindra Sadan, Kolkata",
                        "https://images.unsplash.com/photo-1524650359799-842906ca8d69?auto=format&fit=crop&w=1600&q=85",
                        now.plus(75, ChronoUnit.DAYS), now.plus(75, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                        now.plus(1, ChronoUnit.MINUTES), null, SaleType.STANDARD,
                        List.of(zone("Main hall", 260, "799"), zone("Front row", 70, "1499"))));
            }
            if (!existingNames.contains("Pune Indie Weekender")) {
                seed(eventService, organizerId, request(
                        "Pune Indie Weekender",
                        "The Mills, Pune",
                        "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1600&q=85",
                        now.plus(14, ChronoUnit.DAYS), now.plus(14, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS),
                        now.minus(2, ChronoUnit.HOURS), null, SaleType.STANDARD,
                        List.of(zone("Courtyard", 280, "699"), zone("Stage view", 90, "1299"))));
            }
            if (!existingNames.contains("Goa Sunset Sessions")) {
                seed(eventService, organizerId, request(
                        "Goa Sunset Sessions",
                        "Miramar Beach Club, Goa",
                        "https://images.unsplash.com/photo-1524368535928-5b5e00ddc76b?auto=format&fit=crop&w=1600&q=85",
                        now.plus(21, ChronoUnit.DAYS), now.plus(21, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS),
                        now.minus(2, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS), SaleType.FLASH,
                        List.of(zone("Beach lawn", 360, "899"), zone("Sunset deck", 80, "1699"))));
            }
        };
    }

    private void seed(EventService eventService, Long organizerId, CreateEventRequest request) {
        eventService.createEvent(organizerId, request);
    }

    private static CreateEventRequest request(String name, String venue, String artworkUrl,
                                              Instant startsAt, Instant endsAt,
                                              Instant saleStartsAt, Instant saleEndsAt, SaleType saleType,
                                              List<CreateZoneRequest> zones) {
        CreateZoneRequest general = zones.get(0);
        CreateZoneRequest premium = zones.size() > 1 ? zones.get(1) : general;
        int premiumCapacity = Math.max(1, premium.capacity() / 2);
        Instant effectiveSaleEndsAt = saleEndsAt == null
                ? startsAt.minus(1, ChronoUnit.MINUTES)
                : saleEndsAt;
        Instant queueOpensAt = saleType == SaleType.FLASH
                ? saleStartsAt.minus(30, ChronoUnit.MINUTES)
                : null;
        EventLayoutRequest layout = new EventLayoutRequest(
                general.capacity(), general.price(),
                premiumCapacity, premium.price(),
                premiumCapacity, premium.price());
        return new CreateEventRequest(name, venue, artworkUrl, startsAt, endsAt, queueOpensAt,
                saleStartsAt, effectiveSaleEndsAt, saleType, layout, null);
    }

    private static CreateZoneRequest zone(String name, int capacity, String price) {
        return new CreateZoneRequest(name, capacity, new BigDecimal(price), null);
    }
}
