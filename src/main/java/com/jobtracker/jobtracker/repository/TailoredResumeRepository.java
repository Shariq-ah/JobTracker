package com.jobtracker.jobtracker.repository;

import com.jobtracker.jobtracker.model.TailoredResume;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TailoredResumeRepository extends MongoRepository<TailoredResume, String> {

    Optional<TailoredResume> findByJobId(String jobId);

    long countByAppliedTrue();
}
