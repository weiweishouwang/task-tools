package com.zhw.quartz.core;

import com.zhw.quartz.bean.JobTriggerInfo;
import org.quartz.*;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.format.datetime.joda.DateTimeParser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by zhw on 2015/11/11 0011.
 * 有关Quartz Job Trigger 的操作
 */
@Service
public class QuartzService implements ApplicationContextAware {
    @Autowired
    private Scheduler scheduler;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 增加jobs
     *
     * @param quartzJob
     * @throws SchedulerException
     */
    public void addJob(JobDetailImpl quartzJob) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob((Class<? extends Job>) applicationContext.getType(quartzJob.getName()))//.ofType(JobAdapter.class)
                //.usingJobData("serviceName", quartzJob.getName())
                .withDescription(quartzJob.getDescription())
                .withIdentity(quartzJob.getName(), quartzJob.getGroup()).storeDurably().build();
        scheduler.addJob(jobDetail, true);
    }

    /**
     * 增加Trigger
     *
     * @throws SchedulerException
     */
    public void addTrigger(CronTriggerImpl quartzTrigger) throws SchedulerException {
        CronTrigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(quartzTrigger.getName() + "Trigger", quartzTrigger.getGroup())
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(quartzTrigger.getCronExpression()))
                .withDescription(quartzTrigger.getDescription()).forJob(quartzTrigger.getJobName(), quartzTrigger.getJobGroup())
                .build();
        scheduler.unscheduleJob(trigger.getKey());
        scheduler.scheduleJob(trigger);
    }

    /**
     * 修改trigger
     *
     * @throws SchedulerException
     */
    public void updateTrigger(CronTriggerImpl quartzTrigger) throws SchedulerException {
        Trigger oldTrigger = scheduler.getTrigger(TriggerKey.triggerKey(quartzTrigger.getName(), quartzTrigger.getGroup()));
        CronTrigger updateTrigger = TriggerBuilder
                .newTrigger()
                .withIdentity(quartzTrigger.getName(), quartzTrigger.getGroup())
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(quartzTrigger.getCronExpression()))
                .withDescription(oldTrigger.getDescription()).forJob(oldTrigger.getJobKey().getName(), quartzTrigger.getJobGroup())
                .build();
        scheduler.rescheduleJob(oldTrigger.getKey(), updateTrigger);
    }

    /**
     * 暂停Job
     *
     * @throws SchedulerException
     */
    /*public void stopJob(JobDetailImpl quartzJob) throws SchedulerException {
        scheduler.unscheduleJob(TriggerKey.triggerKey(quartzJob.getName(), quartzJob.getGroup()));
    }*/

    /**
     * 删除Job
     *
     * @throws SchedulerException
     */
    public void deleteJob(JobDetailImpl quartzJob) throws SchedulerException {
        scheduler.deleteJob(JobKey.jobKey(quartzJob.getName(), quartzJob.getGroup()));
    }

    /**
     * 删除Trigger
     *
     * @throws SchedulerException
     */
    public void deleteTrigger(CronTriggerImpl quartzTrigger) throws SchedulerException {
        scheduler.unscheduleJob(TriggerKey.triggerKey(quartzTrigger.getName(), quartzTrigger.getGroup()));
    }

    /**
     * 暂停Trigger
     *
     * @throws SchedulerException
     */
    public void pauseTrigger(CronTriggerImpl quartzTrigger) throws SchedulerException {
        scheduler.pauseTrigger(TriggerKey.triggerKey(quartzTrigger.getName(), quartzTrigger.getGroup()));
    }

    /**
     * 重启trigger
     *
     * @param quartzTrigger
     * @throws SchedulerException
     */
    public void resumeTrigger(CronTriggerImpl quartzTrigger) throws SchedulerException {
        CronTriggerImpl oldTrigger = (CronTriggerImpl) scheduler.getTrigger(TriggerKey.triggerKey(quartzTrigger.getName(), quartzTrigger.getGroup()));
        CronTrigger updateTrigger = TriggerBuilder
                .newTrigger()
                .withIdentity(oldTrigger.getName(), oldTrigger.getGroup())
                .withSchedule(
                        CronScheduleBuilder.cronSchedule(oldTrigger.getCronExpression()))
                .withDescription(oldTrigger.getDescription()).forJob(oldTrigger.getJobKey().getName(), oldTrigger.getJobGroup())
                .build();
        scheduler.rescheduleJob(oldTrigger.getKey(), updateTrigger);
    }

    /**
     * 获取Job
     *
     * @param jobGroup
     * @return
     * @throws SchedulerException
     */
    public List<JobDetailImpl> getQuartzJobs(String jobGroup) throws SchedulerException {
        GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupContains(jobGroup);
        Set<JobKey> jobs = scheduler.getJobKeys(matcher);
        List<JobDetailImpl> jobDetails = new ArrayList<>();
        for (JobKey key : jobs) {
            JobDetailImpl job = (JobDetailImpl) scheduler.getJobDetail(key);
            jobDetails.add(job);
        }
        return jobDetails;
    }

    /**
     * 获取Trigger
     *
     * @return
     * @throws SchedulerException
     */
    public List<Map<String, Object>> getQuartzTriggers(String triggerGroup) throws SchedulerException {
        GroupMatcher<TriggerKey> matcher = GroupMatcher.triggerGroupContains(triggerGroup);
        Set<TriggerKey> triggers = scheduler.getTriggerKeys(matcher);
        List<Map<String, Object>> triggerList = new ArrayList<>();

        Trigger trigger = null;
        String state = null;
        for (TriggerKey key : triggers) {
            Map<String, Object> map = new HashMap<>();
            trigger = scheduler.getTrigger(key);
            state = scheduler.getTriggerState(key).toString();
            map.put("state", state);

            copyPropertys(trigger, map);
            triggerList.add(map);
        }
        return triggerList;
    }

    /**
     * 复制对象到Map
     * @param src
     * @param target
     */
    public static void copyPropertys(Object src, Map<String, Object> target){
        if(null != src){
            if (target == null) {
                target = new HashMap<String, Object>();
            }

            BeanWrapper beanWrapper = new BeanWrapperImpl(src);
            PropertyDescriptor[] descriptor = beanWrapper.getPropertyDescriptors();
            for (int i = 0; i < descriptor.length; i++) {
                String key = descriptor[i].getName();
                if(!key.equals("class")){
                    target.put(key, beanWrapper.getPropertyValue(key));
                }
            }
        }
    }

    public boolean deleteJobAndTrigger(String triggerGroup, String jobGroup, String name) throws SchedulerException {
        CronTriggerImpl quartzTrigger = new CronTriggerImpl();
        quartzTrigger.setName(name);
        quartzTrigger.setGroup(triggerGroup);
        deleteTrigger(quartzTrigger);

        JobDetailImpl quartzJob = new JobDetailImpl();
        quartzJob.setName(name);
        quartzJob.setGroup(jobGroup);
        deleteJob(quartzJob);
        return true;
    }

    public boolean addJobAndTrigger(String triggerGroup, String jobGroup, String name, String cronExpression, String description)
            throws SchedulerException, ParseException {
        if (applicationContext.containsBean(name)) {
            JobDetailImpl quartzJob = new JobDetailImpl();
            quartzJob.setGroup(jobGroup);
            quartzJob.setName(StringUtils.uncapitalize(name));
            addJob(quartzJob);

            CronTriggerImpl quartzTrigger = new CronTriggerImpl();
            quartzTrigger.setCronExpression(cronExpression);
            quartzTrigger.setJobName(name);
            quartzTrigger.setDescription(description);
            quartzTrigger.setName(name);
            quartzTrigger.setGroup(triggerGroup);
            quartzTrigger.setJobGroup(jobGroup);
            addTrigger(quartzTrigger);

            return true;
        } else {
            return false;
        }
    }

    public List<JobTriggerInfo> getJobAndTriggers(String triggerGroup) throws SchedulerException {
        GroupMatcher<TriggerKey> matcher = GroupMatcher.triggerGroupContains(triggerGroup);
        Set<TriggerKey> triggers = scheduler.getTriggerKeys(matcher);
        List<JobTriggerInfo> jobTriggerInfos = new ArrayList<>();

        CronTriggerImpl trigger = null;
        for (TriggerKey key : triggers) {
            trigger = (CronTriggerImpl) scheduler.getTrigger(key);
            JobKey jobKey = new JobKey(trigger.getJobName(), trigger.getJobGroup());
            JobDetailImpl job = (JobDetailImpl) scheduler.getJobDetail(jobKey);

            JobTriggerInfo jobTriggerInfo = new JobTriggerInfo();
            jobTriggerInfo.setTrigerGroup(trigger.getGroup());
            jobTriggerInfo.setTrigerName(trigger.getName());
            jobTriggerInfo.setCronEx(trigger.getCronExpression());
            jobTriggerInfo.setStartTime(trigger.getStartTime());
            jobTriggerInfo.setEndTime(trigger.getEndTime());
            jobTriggerInfo.setPreviousFireTime(trigger.getPreviousFireTime());
            jobTriggerInfo.setNextFireTime(trigger.getNextFireTime());
            jobTriggerInfo.setState(scheduler.getTriggerState(key).toString());
            jobTriggerInfo.setDescription(trigger.getDescription());
            jobTriggerInfo.setJobGroup(job.getGroup());
            jobTriggerInfo.setJobName(job.getName());
            jobTriggerInfo.setDurability(job.isDurable());

            jobTriggerInfos.add(jobTriggerInfo);
        }

        return jobTriggerInfos;
    }

    public List<Map<String, Object>> getJobAndTriggers2(String triggerGroup) throws SchedulerException {
        List<JobTriggerInfo> ret = getJobAndTriggers(triggerGroup);
        return ret.stream().map(jobTriggerInfo -> {
            Map<String, Object> map = new HashMap<>();
            map.put("jobName", jobTriggerInfo.getJobName());
            map.put("description", jobTriggerInfo.getDescription());
            map.put("cronEx", jobTriggerInfo.getCronEx());
            map.put("state", jobTriggerInfo.getState());
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            map.put("previousFireTime", getDateStr(jobTriggerInfo.getPreviousFireTime()));
            map.put("nextFireTime", getDateStr(jobTriggerInfo.getNextFireTime()));
            map.put("startTime", getDateStr(jobTriggerInfo.getStartTime()));
            return map;
        }).collect(Collectors.toList());
    }

    public void execJob(String name) {
        BaseJob job = (BaseJob) applicationContext.getBean(name);
        job.execute();
    }

    private String getDateStr(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (date != null) {
            return formatter.format(date);
        } else {
            return "";
        }
    }
}
