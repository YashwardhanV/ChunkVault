package com.chunkvault.api;

import com.chunkvault.dto.GcResultResponse;
import com.chunkvault.dto.GcStatusResponse;
import com.chunkvault.dto.StorageStatsResponse;
import com.chunkvault.dto.VerifyRequest;
import com.chunkvault.dto.VerifyResponse;
import com.chunkvault.service.GarbageCollectionService;
import com.chunkvault.service.IntegrityVerificationService;
import com.chunkvault.service.StorageStatsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storage")
public class StorageController {
    private final StorageStatsService stats;
    private final IntegrityVerificationService verification;
    private final GarbageCollectionService garbageCollection;

    public StorageController(StorageStatsService stats, IntegrityVerificationService verification,
                             GarbageCollectionService garbageCollection) {
        this.stats = stats;
        this.verification = verification;
        this.garbageCollection = garbageCollection;
    }

    @GetMapping("/stats")
    public StorageStatsResponse stats() {
        return stats.getStats();
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        return verification.verify(request);
    }

    @PostMapping("/gc")
    public GcResultResponse garbageCollect() {
        return garbageCollection.collect();
    }

    @GetMapping("/gc/status")
    public GcStatusResponse garbageCollectionStatus() {
        return garbageCollection.status();
    }
}
