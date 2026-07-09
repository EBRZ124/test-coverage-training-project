package lv.bootcamp.shelter.service;


import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.*;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Write tests for AdoptionEligibilityService.
 * The class and mocks are set up — the rest is yours.
 */
@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    private static final Long ADOPTER_ID = 1L;
    private static final Long ANIMAL_ID = 2L;

    private Adopter testAdopter;
    private Animal testAnimal;

    @BeforeEach
    void setUp(){
        testAdopter = new Adopter(ADOPTER_ID, "James Ferdinand", "jamesferdinand@hotmail.com", 26, 0, 0, false, false);
        testAnimal = new Animal(ANIMAL_ID, "Rex", AnimalType.DOG, "Daschund", 3, "Chill", AnimalStatus.AVAILABLE);
    }
    // Write your tests here

    @Test
    void adopterNotFound_returnsReject(){
        when(adopterRepository.findById(1L)).thenReturn(Optional.empty());

        AdoptionResult adoptionResult = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);
        assertThat(adoptionResult.approved()).isFalse();
        assertThat(adoptionResult.reason()).isEqualTo(RejectionReasons.ADOPTER_NOT_FOUND);

        verifyNoInteractions(animalRepository, notificationClient, auditLogger);
    }

    @Test
    void animalNotFound_returnsReject(){
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(testAdopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.empty());

        AdoptionResult adoptionResult = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(adoptionResult.approved()).isFalse();
        assertThat(adoptionResult.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_FOUND);
        verifyNoInteractions(notificationClient, auditLogger);
    }

    @Test
    void animalNotAvailable_returnsReject(){
        testAnimal.setStatus(AnimalStatus.ADOPTED);
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(testAdopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(testAnimal));

        AdoptionResult adoptionResult = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(adoptionResult.approved()).isFalse();
        assertThat(adoptionResult.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_AVAILABLE);
        verifyNoInteractions(notificationClient, auditLogger);
    }

    @Test
    void underageAdopeter_getsRejected(){
        testAdopter.setAge(15);
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(testAdopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(testAnimal));

        AdoptionResult adoptionResult = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);
        assertThat(adoptionResult.approved()).isFalse();
        assertThat(adoptionResult.reason()).isEqualTo(RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(ADOPTER_ID, ANIMAL_ID, RejectionReason.UNDERAGE);
        verifyNoInteractions(notificationClient);

    }

    @Test
    void adopterIsExactlyEighteen_isNotRejected(){
        testAdopter.setAge(18);
        when(adopterRepository.findById(ADOPTER_ID)).thenReturn(Optional.of(testAdopter));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(testAnimal));

        AdoptionResult result = service.evaluateAdoption(ADOPTER_ID, ANIMAL_ID);

        assertThat(result.approved()).isTrue();
        verify(auditLogger, never()).logRejection(anyLong(), anyLong(), eq(RejectionReason.UNDERAGE));

    }


    @Nested
    @DisplayName("calculatePriorityScore")
    class CalulatePriorityTests {

        @Test
        void noFactors_scoreEqualsZero(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 0, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isZero();
        }

        @Test
        void somePreviousAdoptions_scoreEqualsTen() {
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 2, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(10);
        }

        @Test
        void exactlyThreePriorAdoptions_scoreEqualsTen() {
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 3, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(10);
        }

        @Test
        void moreThanThreePriorAdoptions_scoreEqualsFifteen() {
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 4, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(15);
        }

        @Test
        void wayMoreThanThreePriorAdoptions_scoreEqualsFifteen() {
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 15, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(15);
        }

        @Test
        void largePropertyIsTrue_scoreEqualsFifteen(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 0, true, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(15);
        }

        @Test
        void largePropertyAndSomePriorAdoptions_scoreEqualsTwentyFive(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 3, true, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(25);
        }

        @Test
        void largePropertyAndManyPriorAdoptions_scoreEqualsThirty(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 4, true, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "Orange", 3, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(30);
        }

        @Test
        void adoptingOldAnimal_scoreEqualsTwenty(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 0, false, false);
            Animal regularAnimal = new Animal(1L, "Hairloss", AnimalType.CAT, "Orange", 9, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(20);
        }

        @Test
        void adoptingAnimalAgedSeven_scoreEqualsZero(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 0, false, false);
            Animal regularAnimal = new Animal(1L, "Hairloss", AnimalType.CAT, "Orange", 7, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(0);
        }

        @Test
        void adoptingOldAnimalToABigHouse_scoreEqualsThirtyFive(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 0, true, false);
            Animal regularAnimal = new Animal(1L, "Hairloss", AnimalType.CAT, "Orange", 9, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(35);
        }

        @Test
        void adoptingOldAnimalToABigHouseWithSomePriorAdoptions_scoreEqualsFourtyFive(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 3, true, false);
            Animal regularAnimal = new Animal(1L, "Hairloss", AnimalType.CAT, "Orange", 9, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(45);
        }

        @Test
        void adoptingOldAnimalToABigHouseWithManyPriorAdoptions_scoreEqualsFifty(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 0, 4, true, false);
            Animal regularAnimal = new Animal(1L, "Hairloss", AnimalType.CAT, "Orange", 9, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(50);
        }

        @Test
        void adoptingWithOnePetsExistingButNoPriorAdoptions_scoreEqualsZero(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 1, 0, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "White", 2, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(0);
        }

        @Test
        void adoptingWithManyPetsExisting_scoreEqualsZero_scoreIsNeverNegative(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 10, 0, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "White", 2, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(0);
        }

        @Test
        void adoptingWihtTwoPetsExistingAndAdoptingOnePetPrior_scoreEqualsSix(){
            Adopter regularAdopter = new Adopter(1L, "James", "James@linux.com", 33, 2, 1, false, false);
            Animal regularAnimal = new Animal(1L, "Fluffer", AnimalType.CAT, "White", 2, "Name checks out", AnimalStatus.AVAILABLE);

            assertThat(service.calculatePriorityScore(regularAdopter, regularAnimal)).isEqualTo(6);
        }

    }
}
