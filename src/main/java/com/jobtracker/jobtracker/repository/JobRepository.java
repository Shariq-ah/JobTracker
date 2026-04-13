package com.jobtracker.jobtracker.repository;

import com.jobtracker.jobtracker.model.Job;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobRepository extends MongoRepository<Job, String> {

}
