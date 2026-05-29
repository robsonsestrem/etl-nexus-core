package com.etl.importer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.etl.importer.domain.Patient;

import java.util.Optional;

@Repository
public interface PatientRepository extends MongoRepository<Patient, String> {
    Optional<Patient> findByCpf(String cpf);
    Optional<Patient> findByMatriculaSap(String matriculaSap);
    Optional<Patient> findByChaveUnica(String chaveUnica);
}
