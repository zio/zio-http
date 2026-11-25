$version: "2"
namespace example.petstore

/// A simple pet store service
service PetStore {
    version: "1.0.0"
    operations: [GetPet, ListPets, CreatePet, DeletePet]
}

/// Get a pet by ID
@http(method: "GET", uri: "/pets/{petId}")
@readonly
operation GetPet {
    input: GetPetInput
    output: GetPetOutput
    errors: [PetNotFound]
}

/// List all pets with pagination
@http(method: "GET", uri: "/pets")
@readonly
@paginated(inputToken: "nextToken", outputToken: "nextToken", pageSize: "maxResults", items: "pets")
operation ListPets {
    input: ListPetsInput
    output: ListPetsOutput
}

/// Create a new pet
@http(method: "POST", uri: "/pets")
@idempotent
operation CreatePet {
    input: CreatePetInput
    output: CreatePetOutput
    errors: [ValidationError]
}

/// Delete a pet by ID
@http(method: "DELETE", uri: "/pets/{petId}")
@idempotent
operation DeletePet {
    input: DeletePetInput
    output: DeletePetOutput
    errors: [PetNotFound]
}

structure GetPetInput {
    @required
    @httpLabel
    petId: String
}

structure GetPetOutput {
    @required
    pet: Pet
}

structure ListPetsInput {
    @httpQuery("maxResults")
    @range(min: 1, max: 100)
    maxResults: Integer

    @httpQuery("nextToken")
    nextToken: String

    @httpQuery("species")
    species: Species
}

structure ListPetsOutput {
    @required
    pets: PetList

    nextToken: String
}

structure CreatePetInput {
    @required
    name: PetName

    @required
    species: Species

    age: Integer
    
    tags: TagList
}

structure CreatePetOutput {
    @required
    pet: Pet
}

structure DeletePetInput {
    @required
    @httpLabel
    petId: String
}

structure DeletePetOutput {}

/// A pet in the store
structure Pet {
    @required
    id: String

    @required
    name: PetName

    @required
    species: Species

    age: Integer

    @required
    createdAt: Timestamp

    tags: TagList
}

/// Pet species
enum Species {
    DOG
    CAT
    BIRD
    FISH
    REPTILE
    OTHER
}

/// Pet name with length validation
@length(min: 1, max: 100)
string PetName

list PetList {
    member: Pet
}

list TagList {
    member: Tag
}

@length(min: 1, max: 50)
string Tag

/// Pet not found error
@error("client")
@httpError(404)
structure PetNotFound {
    @required
    message: String

    petId: String
}

/// Validation error
@error("client")
@httpError(400)
structure ValidationError {
    @required
    message: String

    fieldErrors: FieldErrorList
}

structure FieldError {
    @required
    field: String

    @required
    message: String
}

list FieldErrorList {
    member: FieldError
}
