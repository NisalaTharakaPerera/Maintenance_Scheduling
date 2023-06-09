package org.acme.maintenancescheduling.bootstrap;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;


import org.acme.maintenancescheduling.domain.Crew;
import org.acme.maintenancescheduling.domain.Job;
import org.acme.maintenancescheduling.domain.WorkCalendar;
import org.acme.maintenancescheduling.persistence.CrewRepository;
import org.acme.maintenancescheduling.persistence.JobRepository;
import org.acme.maintenancescheduling.persistence.WorkCalendarRepository;
import org.acme.maintenancescheduling.solver.EndDateUpdatingVariableListener;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class DemoDataGenerator {

    @ConfigProperty(name = "schedule.demoData", defaultValue = "SMALL")
    public DemoData demoData;

    public enum DemoData {
        NONE,
        SMALL,
        LARGE
    }

    @Inject
    WorkCalendarRepository workCalendarRepository;
    @Inject
    CrewRepository crewRepository;
    @Inject
    JobRepository jobRepository;

    @Transactional
    public void generateDemoData(@Observes StartupEvent startupEvent) {
        if (demoData == DemoData.NONE) {
            return;
        }

        List<Crew> crewList = new ArrayList<>();
        crewList.add(new Crew("Crew 1"));
        crewList.add(new Crew("Crew 2"));
        crewList.add(new Crew("Crew 3"));
        if (demoData == DemoData.LARGE) {
            crewList.add(new Crew("Crew 4"));
            crewList.add(new Crew("Crew 5"));
        }
        crewRepository.persist(crewList);

        LocalDate fromDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        int weekListSize = (demoData == DemoData.LARGE) ? 16 : 8;
        LocalDate toDate = fromDate.plusWeeks(weekListSize);
        workCalendarRepository.persist(new WorkCalendar(fromDate, toDate));
        int workdayTotal = weekListSize * 5;

        final String[] JOB_AREA_NAMES = {
                "Area 1", "Area 2", "Area 3", "Area 4", "Area 5", "Area 6", "Area 7", "Area 8", "Area 9",
                "Area 10"};
        final String[] JOB_TARGET_NAMES = {"Job 1", "Job 2", "Job 3", "Job 4", "Job 5"};

        List<Job> jobList = new ArrayList<>();
        int jobListSize = weekListSize * crewList.size() * 3 / 5;
        int jobAreaTargetLimit = Math.min(JOB_TARGET_NAMES.length, crewList.size() * 2);
        Random random = new Random(17);
        for (int i = 0; i < jobListSize; i++) {
            String jobArea = JOB_AREA_NAMES[i / jobAreaTargetLimit];
            String jobTarget = JOB_TARGET_NAMES[i % jobAreaTargetLimit];
            // 1 day to 2 workweeks (1 workweek on average)
            int durationInDays = 1 + random.nextInt(10);
            int readyDueBetweenWorkdays = durationInDays + 5 // at least 5 days of flexibility
                    + random.nextInt(workdayTotal - (durationInDays + 5));
            int readyWorkdayOffset = random.nextInt(workdayTotal - readyDueBetweenWorkdays + 1);
            int readyIdealEndBetweenWorkdays = readyDueBetweenWorkdays - 1 - random.nextInt(4);
            LocalDate readyDate = EndDateUpdatingVariableListener.calculateEndDate(fromDate, readyWorkdayOffset);
            LocalDate dueDate = EndDateUpdatingVariableListener.calculateEndDate(readyDate, readyDueBetweenWorkdays);
            LocalDate idealEndDate = EndDateUpdatingVariableListener.calculateEndDate(readyDate, readyIdealEndBetweenWorkdays);
            Set<String> tagSet = random.nextDouble() < 0.1 ? Set.of(jobArea) : Set.of(jobArea);
            jobList.add(new Job(jobArea + " " + jobTarget, durationInDays, readyDate, dueDate, idealEndDate, tagSet));
        }

        jobRepository.persist(jobList);
    }

}
