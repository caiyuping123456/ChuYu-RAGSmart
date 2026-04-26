from langchain_openai import ChatOpenAI

llm = ChatOpenAI (
    base_url="https://ruoli.dev/v1",
    openai_api_key="sk-noLAByir2hWGrFRaYQ164E9zABhQNs3uHIUrXlRvoGWmkrIA",
    model="gpt-5.2"
)

for item in llm.stream("什么是Java"):
    print(item)

# "Authorization": "Bearer sk-noLAByir2hWGrFRaYQ164E9zABhQNs3uHIUrXlRvoGWmkrIA",
# "Authorization": "Bearer sk-eejpytshnxihqbeedqsgfuixodazjxjxjetbounsqbzzygtr",
