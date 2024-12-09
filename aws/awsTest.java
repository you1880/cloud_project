package aws;
/*
* Cloud Computing
* 
* Dynamic Resource Management Tool
* using AWS Java SDK Library
* 
*/
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeRegionsResult;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetCommandInvocationRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetCommandInvocationResult;
import com.amazonaws.services.simplesystemsmanagement.model.SendCommandRequest;
import com.amazonaws.services.simplesystemsmanagement.model.SendCommandResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RebootInstancesResult;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Filter;

public class awsTest {
	private static AmazonEC2      ec2;
	private static ArrayList<Instance> instanceList = new ArrayList<>();

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static Set<Integer> requestedInstances = new HashSet<Integer>();

	private static final String STATE_RUNNING = "running";
	private static final String STATE_STOP = "stopped";
	private static final String STATE_PENDING = "pending";
	private static final String STATE_STOPPING = "stopping";

	private static void init() throws Exception {

		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. " +
					"Please make sure that your credentials file is at the correct " +
					"location (~/.aws/credentials), and is in valid format.",
					e);
		}
		ec2 = AmazonEC2ClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion("ap-southeast-2")	/* check the region at AWS console */
			.build();
	}

	public static void main(String[] args) throws Exception {

		init();
		initInstanceList();
		updateInstanceList();

		Scanner menu = new Scanner(System.in);
		Scanner idScanner = new Scanner(System.in);
		Scanner nameScanner = new Scanner(System.in);
		int number = 0;
		
		while(true) {
			System.out.println("                                                            ");
			System.out.println("                                                            ");
			System.out.println("------------------------------------------------------------");
			System.out.println("           Amazon AWS Control Panel using SDK               ");
			System.out.println("------------------------------------------------------------");
			System.out.println("  1. list instance                2. available zones        ");
			System.out.println("  3. start instance               4. available regions      ");
			System.out.println("  5. stop instance                6. create instance        ");
			System.out.println("  7. reboot instance              8. list images            ");
			System.out.println("  9. condor status                99. quit                  ");
			System.out.println("------------------------------------------------------------");
			System.out.print("Enter an integer: ");
			
			if(menu.hasNextInt()) {
				number = menu.nextInt();
				} else {
					System.out.println("concentration!");
					continue;
				}
			

			int instanceNum = -1;

			switch(number) {
				case 1: 
					listInstances();
					break;
				case 2: 
					availableZones();
					break;
				case 3: 
					System.out.printf("Enter instance number[0-%d] : ", instanceList.size() - 1);
					if(idScanner.hasNextInt()) {
						instanceNum = idScanner.nextInt();
						startInstance(instanceNum);
					}
					break;
				case 4: 
					availableRegions();
					break;
				case 5: 
					System.out.printf("Enter instance number[0-%d] : ", instanceList.size() - 1);
					if(idScanner.hasNextInt()) {
						instanceNum = idScanner.nextInt();
						stopInstance(instanceNum);
					}
					break;
				case 6:
					System.out.print("Enter ami Name: ");
					String amiId = "";
					if(idScanner.hasNext())
						amiId = idScanner.nextLine();
					
					System.out.print("Enter instance name : ");
					String instanceName = "";
					if(nameScanner.hasNext())
						instanceName = nameScanner.nextLine();
					
					if(!amiId.trim().isEmpty() && !instanceName.trim().isEmpty())
						createInstance(amiId, instanceName);
					break;
				case 7: 
					System.out.printf("Enter instance number[0-%d] : ", instanceList.size() - 1);
					if(idScanner.hasNextInt()) {
						instanceNum = idScanner.nextInt();
						rebootInstance(instanceNum);
					}
					break;
				case 8: 
					listImages();
					break;
				case 9:
					execCondorStatus();
					break;
				case 99: 
					System.out.println("bye!");
					scheduler.shutdown();
					menu.close();
					idScanner.close();
					nameScanner.close();
					return;
				default: 
					System.out.println("concentration!");
					continue;
			}
		}
	}

	public static void initInstanceList() {
		System.out.println("Loading Instances...");
		boolean done = false;
		int index = 0;

		instanceList.clear();

		DescribeInstancesRequest request = new DescribeInstancesRequest();

		while(!done) {
			DescribeInstancesResult response = ec2.describeInstances(request);

			for(Reservation reservation : response.getReservations()) {
				for(Instance instance : reservation.getInstances()) {
					String state = instance.getState().getName();

					if(state.equals(STATE_PENDING) || state.equals(STATE_STOPPING)) {
						requestedInstances.add(index);
					}

					instanceList.add(instance);
					index++;
				}
			}

			request.setNextToken(response.getNextToken());
			if(response.getNextToken() == null) {
				done = true;
			}
		}

		System.out.println("Loading Instances Done [Current Instances : " + instanceList.size() + "]");
	}

	public static void updateInstanceList() {
		Runnable updateTask = () -> {
			if(!requestedInstances.isEmpty()) {
				Iterator<Integer> iterator = requestedInstances.iterator();

				while(iterator.hasNext()) {
					int instanceNum = iterator.next();
					String instanceId = instanceList.get(instanceNum).getInstanceId();

					try {
						DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
						DescribeInstancesResult response = ec2.describeInstances(request);

						Instance instance = response.getReservations().get(0).getInstances().get(0);
						String instanceState = instance.getState().getName();

						if(instanceState.equals(STATE_PENDING) || instanceState.equals(STATE_STOPPING)) {
							continue;
						} 
						else {
							synchronized(instanceList) {
								instanceList.set(instanceNum, instance);
							}
							iterator.remove();
						}
					} catch (Exception e) {
						System.out.println("Error : " + e.toString());
					}
				}
			}
		};
		scheduler.scheduleAtFixedRate(updateTask, 0, 1, TimeUnit.SECONDS);
	}

	public static void listInstances() {
		System.out.println("Listing Instances...");
		
		for(int idx = 0; idx < instanceList.size(); idx++) {
			Instance instance = instanceList.get(idx);
			System.out.printf("[Instance %d], [id] %s, [state] %s, [type] %s, [AMI] %s, [monitoring state] %s\n",
				idx, 
				instance.getInstanceId(), 
				instance.getState().getName(), 
				instance.getInstanceType(), 
				instance.getImageId(), 
				instance.getMonitoring().getState());
		}
	}
	
	public static void availableZones()	{
		System.out.println("Available zones....");

		try {
			DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
			Iterator <AvailabilityZone> iterator = availabilityZonesResult.getAvailabilityZones().iterator();
			
			AvailabilityZone zone;
			while(iterator.hasNext()) {
				zone = iterator.next();
				System.out.printf("[id] %s,  [region] %15s, [zone] %15s\n", zone.getZoneId(), zone.getRegionName(), zone.getZoneName());
			}
			System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() + " Availability Zones.");
		} catch (AmazonServiceException ase) {
			System.out.println("Caught Exception: " + ase.getMessage());
			System.out.println("Reponse Status Code: " + ase.getStatusCode());
			System.out.println("Error Code: " + ase.getErrorCode());
			System.out.println("Request ID: " + ase.getRequestId());
		}
	}

	public static void startInstance(int instanceNum) {
		if(instanceNum < 0 || instanceNum >= instanceList.size()) {
			System.out.println("Invalid Instance Number");
			return;
		}

		Instance instance = instanceList.get(instanceNum);
		String instanceId = instance.getInstanceId();

		if(requestedInstances.contains(instanceNum)) {
			System.out.printf("Instance %s already in queue\n", instanceId);
			return;
		}

		if(!instance.getState().getName().equals(STATE_STOP)) {
			System.out.printf("[Instance %s] Current State : %s\n", instanceId, instance.getState().getName());
			return;
		}

		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		System.out.printf("Starting... %s\n", instanceId);

		try {
			StartInstancesRequest request = new StartInstancesRequest().withInstanceIds(instanceId);
			ec2.startInstances(request);

			requestedInstances.add(instanceNum);

			System.out.printf("Instance %s successfully started", instanceId);
		} catch (Exception e) {
			System.out.println("Exception : " + e.toString());
		}
	}
	
	public static void availableRegions() {
		System.out.println("Available regions ....");
		
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		DescribeRegionsResult regionsResponse = ec2.describeRegions();

		for(Region region : regionsResponse.getRegions()) {
			System.out.printf(
				"[region] %15s, " +
				"[endpoint] %s\n",
				region.getRegionName(),
				region.getEndpoint());
		}
	}

	public static void stopInstance(int instanceNum) {
		if(instanceNum < 0 || instanceNum >= instanceList.size()) {
			System.out.println("Invalid Instance Number");
			return;
		}

		Instance instance = instanceList.get(instanceNum);
		String instanceId = instance.getInstanceId();

		if(requestedInstances.contains(instanceNum)) {
			System.out.printf("Instance %s already in queue\n", instanceId);
			return;
		}

		if(!instance.getState().getName().equals(STATE_RUNNING)) {
			System.out.printf("[Instance %s] Current State : %s\n", instanceId, instance.getState().getName());
			return;
		}

		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		System.out.printf("Stop Instance %s...\n", instanceId);

		try {
			StopInstancesRequest request = new StopInstancesRequest().withInstanceIds(instanceId);
			ec2.stopInstances(request);
			
			requestedInstances.add(instanceNum);

			System.out.printf("Instance %s successfully stopped", instanceId);
		} catch (Exception e) {
			System.out.println("Exception : " + e.toString());
		}
	}
	
	public static void createInstance(String amiName, String instanceName) {
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		final String securityGroupName = "HTCondor";
		final String keyName = "cloud-key";

		String amiId = getImageId(amiName);
		if(amiId == null) {
			System.out.println("Invalid AMI name\n");
			return;
		}
		try {
			RunInstancesRequest runRequest = new RunInstancesRequest()
			.withImageId(amiId)
			.withInstanceType(InstanceType.T2Micro)
			.withMaxCount(1)
			.withMinCount(1)
			.withSecurityGroups(securityGroupName)
			.withKeyName(keyName);

			RunInstancesResult runResponse = ec2.runInstances(runRequest);
			String reservationId = runResponse.getReservation().getInstances().get(0).getInstanceId();

			Tag tag = new Tag().withKey("Name").withValue(instanceName);
			CreateTagsRequest createTagsRequest = new CreateTagsRequest().withResources(reservationId).withTags(tag);
			ec2.createTags(createTagsRequest);
			
			Thread.sleep(2000);
			System.out.printf("Successfully Started EC2 instance %s with AMI %s\n", reservationId, amiId);
			initInstanceList();
		} catch(Exception e) {
			System.out.println("Exception : " + e.toString());
		}
	}
	
	private static String getImageId(String amiName) {
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		
		try {
			DescribeImagesRequest request = new DescribeImagesRequest();
			ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();

			request.getFilters().add(new Filter().withName("owner-id").withValues("537124971887"));
			request.setRequestCredentialsProvider(credentialsProvider);

			DescribeImagesResult result = ec2.describeImages(request);

			for(Image images : result.getImages()) {
				if(amiName.equals(images.getName())) {
					return images.getImageId();
				}
			}
		} catch (Exception e) {
			System.out.println("Exception : " + e.toString());
		}

		return null;
	}

	public static void rebootInstance(int instanceNum) {
		if(instanceNum < 0 || instanceNum >= instanceList.size()) {
			System.out.println("Invalid Instance Number");
			return;
		}

		Instance instance = instanceList.get(instanceNum);
		String instanceId = instance.getInstanceId();

		if(!instance.getState().getName().equals(STATE_RUNNING)) {
			System.out.printf("[Instance %s] Current State : %s\n", instanceId, instance.getState().getName());
			return;
		}

		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		System.out.printf("Rebooting Instance %s...\n", instanceId);

		try {
			RebootInstancesRequest request = new RebootInstancesRequest().withInstanceIds(instanceId);
			RebootInstancesResult response = ec2.rebootInstances(request);

			System.out.printf("Successfully rebooted instance %s", instanceId);
		} catch (Exception e) {
			System.out.println("Exception : " + e.toString());
		}
	}

	public static void listImages() {
		System.out.println("Listing images....");
		
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();
		
		DescribeImagesRequest request = new DescribeImagesRequest();
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		
		request.getFilters().add(new Filter().withName("owner-id").withValues("537124971887"));
		request.setRequestCredentialsProvider(credentialsProvider);
		
		DescribeImagesResult results = ec2.describeImages(request);
		
		for(Image images :results.getImages()){
			System.out.printf("[ImageID] %s, [Name] %s, [Owner] %s\n", 
					images.getImageId(), images.getName(), images.getOwnerId());
		}
	}

	public static void execCondorStatus() {
		String command = "condor_status";
		String masterInstanceId = "i-0a2be53e6aa659aaa";
		AWSSimpleSystemsManagement ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();

		try {
			SendCommandRequest sendCommandRequest = new SendCommandRequest()
				.withInstanceIds(masterInstanceId)
				.withDocumentName("AWS-RunShellScript")
				.addParametersEntry("commands", Collections.singletonList(command));

			SendCommandResult sendCommandResult = ssmClient.sendCommand(sendCommandRequest);
			String commandId = sendCommandResult.getCommand().getCommandId();
			
			GetCommandInvocationRequest invocationRequest = new GetCommandInvocationRequest()
				.withCommandId(commandId)
				.withInstanceId(masterInstanceId);
			
			GetCommandInvocationResult invocationResult;
			while(true) {
				invocationResult = ssmClient.getCommandInvocation(invocationRequest);
				String status = invocationResult.getStatus();

				if(status.equals("InProgress")) {
					Thread.sleep(1000);
				}
				else {
                    break;
				}
			}

			System.out.println("Command Output: " + invocationResult.getStandardOutputContent());
		} catch(Exception e) {
			System.out.println("Exception : " + e.toString());
		}
	}
}