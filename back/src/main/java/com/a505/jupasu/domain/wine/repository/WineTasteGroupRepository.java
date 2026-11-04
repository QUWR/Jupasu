package com.a505.jupasu.domain.wine.repository;

import com.a505.jupasu.domain.wine.entity.WineTasteGroup;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WineTasteGroupRepository extends JpaRepository<WineTasteGroup, Long> {

    @Query("SELECT wtg.tasteGroup.name FROM WineTasteGroup wtg WHERE wtg.wine.id = :wineId")
    List<String> findTasteNamesByWineId(@Param("wineId") Long wineId);
}
