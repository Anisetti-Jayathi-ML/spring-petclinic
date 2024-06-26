/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.CaptureSpan;
import co.elastic.apm.api.Span;
import java.lang.String;
import jakarta.validation.Valid;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Dave Syer
 */
@Controller
class VisitController {

	private final OwnerRepository owners;

	Logger logger = LoggerFactory.getLogger(VisitController.class);

	public VisitController(OwnerRepository owners) {
		this.owners = owners;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	/**
	 * Called before each and every @RequestMapping annotated method. 2 goals: - Make sure
	 * we always have fresh data - Since we do not use the session scope, make sure that
	 * Pet object always has an id (Even though id is not part of the form fields)
	 * @param petId
	 * @return Pet
	 */
	@ModelAttribute("visit")
	public Visit loadPetWithVisit(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			Map<String, Object> model) {
		Owner owner = this.owners.findById(ownerId);

		Pet pet = owner.getPet(petId);
		model.put("pet", pet);
		model.put("owner", owner);

		Visit visit = new Visit();
		pet.addVisit(visit);
		return visit;
	}

	// Spring MVC calls method loadPetWithVisit(...) before initNewVisitForm is
	// called
	@GetMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	@CaptureSpan
	public String initNewVisitForm(HttpSession session) {
		if (session.getAttribute("username") == null) {
			return "login";
		}
		Span span = ElasticApm.currentSpan();
		span.addLabel("_tag_user", String.valueOf(session.getAttribute("username")));
		logger.info("User:" + session.getAttribute("username")
				+ " made the request  GET /owners/*/pets/{petId}/visits/new");
		logger.info("Create or update visit form rendered");
		return "pets/createOrUpdateVisitForm";
	}

	// Spring MVC calls method loadPetWithVisit(...) before processNewVisitForm is
	// called
	@PostMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	@CaptureSpan
	public String processNewVisitForm(@RequestBody String requestBody, HttpSession session, @ModelAttribute Owner owner,
			@PathVariable int petId, @Valid Visit visit, BindingResult result) {
		logger.info("Request Body: " + requestBody);
		if (session.getAttribute("username") == null) {
			return "login";
		}
		Span span = ElasticApm.currentSpan();
		span.addLabel("_tag_user", String.valueOf(session.getAttribute("username")));
		if (result.hasErrors()) {
			logger.error("Error occured in creation/updation of visit for petId: " + petId);
			logger.info("Create or updae visit form rendered");
			return "pets/createOrUpdateVisitForm";
		}

		owner.addVisit(petId, visit);
		this.owners.save(owner);
		logger.info("User:" + session.getAttribute("username") + " made the request POST /owners/{ownerId}/pets/"
				+ petId + "/visits/new");
		logger.info("Visit created and added to the database successfully");
		logger.info("Fetching updated owner details from db - /owners/" + owner.getId());
		return "redirect:/owners/{ownerId}";
	}

}
