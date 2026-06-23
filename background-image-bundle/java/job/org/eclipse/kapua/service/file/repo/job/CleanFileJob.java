package org.eclipse.kapua.service.file.repo.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.service.file.repo.FileRepoService;;

public class CleanFileJob implements Job {
	private final KapuaLocator locator = KapuaLocator.getInstance();
	private final FileRepoService fileRepoService = locator.getService(FileRepoService.class);
	 public CleanFileJob() {
	 }
	 public void execute(JobExecutionContext context)
			 throws JobExecutionException
	 {
		 System.err.println("CleanFileJob is executing!");
		 fileRepoService.cleanTtlFile();
	 }
 }