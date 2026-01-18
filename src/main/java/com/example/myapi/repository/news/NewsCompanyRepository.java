package com.example.myapi.repository.news;

import com.example.myapi.entity.NewsCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NewsCompanyRepository extends JpaRepository<NewsCompany, Long> {
    
    @Query(value = "SELECT * FROM bs4news_news_company WHERE News_Company_Code = :code LIMIT 1", nativeQuery = true)
    Optional<NewsCompany> findByNewsCompanyCode(@Param("code") String code);
}
