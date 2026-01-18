package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bs4news_news_company")
public class NewsCompany {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "News_Company_Code")
    private String newsCompanyCode;

    @Column(name = "News_Company_Name")
    private String newsCompanyName;

    @Column(name = "ETC1", columnDefinition = "LONGTEXT")
    private String etc1;

    @Column(name = "ETC2", columnDefinition = "LONGTEXT")
    private String etc2;

    @Column(name = "ETC3", columnDefinition = "LONGTEXT")
    private String etc3;

    @Column(name = "ETC4", columnDefinition = "LONGTEXT")
    private String etc4;

    @Column(name = "ETC5", columnDefinition = "LONGTEXT")
    private String etc5;

    @Column(name = "News_Company_CreateDT")
    private LocalDateTime newsCompanyCreateDT;

    @Column(name = "News_Company_UpdateDT")
    private LocalDateTime newsCompanyUpdateDT;
}
